package co.ledger.cria.domain.models

import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{
  BlockHash,
  BlockHeight,
  BlockView,
  Derivation,
  InputView,
  OutputView,
  Satoshis,
  SatoshisTestHelper,
  TransactionView,
  TransactionViewTestHelper
}
import co.ledger.cria.domain.models.keychain.ChangeType
import org.scalacheck.{Arbitrary, Gen}
import cats.implicits._
import org.scalacheck.cats.implicits._

import java.time.{Instant, ZoneId, ZonedDateTime}

object Generators {
  implicit val arbAccountUid: Arbitrary[AccountUid] =
    Arbitrary(Gen.uuid.map(uuid => AccountUid("account_uid_" + uuid.toString)))

  implicit val arbSHA256: Arbitrary[String] =
    Arbitrary(Gen.stringOfN(64, Gen.hexChar).map(_.toLowerCase))

  implicit val arbTxHash: Arbitrary[TxHash] =
    Arbitrary(arbSHA256.arbitrary.map(TxHash.fromStringUnsafe))

  val minTime: Instant = ZonedDateTime.of(2011, 7, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant

  val maxTime: Instant = ZonedDateTime.of(2021, 7, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant

  val maxBlock = 1000

  implicit val arbBlockHeight: Arbitrary[BlockHeight] =
    Arbitrary(Gen.chooseNum(0L, maxBlock).map(BlockHeight.fromLongUnsafe))

  implicit val arbBlockHash: Arbitrary[BlockHash] =
    Arbitrary(arbSHA256.arbitrary.map(BlockHash.fromStringUnsafe))

  implicit val arbBlockView: Arbitrary[BlockView] =
    Arbitrary {
      for {
        blockHash <- arbBlockHash.arbitrary
        height    <- arbBlockHeight.arbitrary
        timeDelta = java.time.Duration
          .between(maxTime, minTime)
          .multipliedBy(height.value)
          .dividedBy(maxBlock)
        time = minTime.plus(timeDelta)
      } yield BlockView(
        hash = blockHash,
        height = height,
        time = time
      )
    }

  val maxAddress = 100

  implicit val arbDerivation: Arbitrary[Derivation] =
    Arbitrary {
      for {
        chain <- Gen.prob(0.5).map {
          case true  => 0
          case false => 1
        }
        addressNumber <- Gen.chooseNum(0, maxAddress)
      } yield Derivation(chain, addressNumber)
    }

  implicit val arbSatoshis: Arbitrary[Satoshis] =
    Arbitrary {
      Gen.chooseNum(0, 1000).map(l => SatoshisTestHelper.unsafe(BigInt(l)))
    }

  implicit val inputView: Arbitrary[InputView] =
    Arbitrary {
      for {
        txHash      <- arbTxHash.arbitrary
        outputIndex <- Gen.chooseNum(0, 10)
        inputIndex  <- Gen.chooseNum(0, 10)
        value       <- arbSatoshis.arbitrary
        address     <- Gen.alphaStr.map("addr_" + _)
        sequence    <- Gen.choose(0, maxBlock)
        derivation  <- Gen.option(arbDerivation.arbitrary)
      } yield InputView(
        outputHash = txHash,
        outputIndex = outputIndex,
        inputIndex = inputIndex,
        value = value,
        address = address,
        scriptSignature = "SIGSIG",
        txinwitness = Nil,
        sequence = sequence,
        derivation = derivation
      )
    }

  implicit val outputView: Arbitrary[OutputView] =
    Arbitrary {
      for {
        outputIndex <- Gen.chooseNum(0, 10)
        value       <- arbSatoshis.arbitrary
        address     <- Gen.alphaStr.map("addr_" + _)
        scriptHex = "script"
        derivation <- Gen.option(arbDerivation.arbitrary)
        changeType = derivation.map { d =>
          d.chain match {
            case 0 => ChangeType.External
            case 1 => ChangeType.Internal
          }
        }
      } yield OutputView(
        outputIndex = outputIndex,
        value = value,
        address = address,
        scriptHex = scriptHex,
        derivation = derivation,
        changeType = changeType
      )
    }

  val maxOutputs = 30

  implicit val arbTransactionView: Arbitrary[TransactionView] =
    Arbitrary {
      for {
        id    <- Gen.alphaStr.map("tx_id_" + _)
        hash  <- arbTxHash.arbitrary
        block <- Gen.option(arbBlockView.arbitrary)
        receivedAt = block.fold(maxTime.plusSeconds(30))(b => b.time.plusSeconds(10))
        lockTime   = 10
        outputs <- Gen
          .chooseNum(0, 4)
          .map(n => (0 until n).toList)
          .flatMap(indices =>
            indices.traverse(i => outputView.arbitrary.map(_.copy(outputIndex = i)))
          )
        inputsAndFees <- mkInputs(outputs)
        (fees, inputs) = inputsAndFees
      } yield TransactionViewTestHelper.unsafe(
        id = id,
        hash = hash,
        receivedAt = receivedAt,
        lockTime = lockTime,
        fees = fees,
        inputs = inputs,
        outputs = outputs,
        block = block,
        confirmations = block.fold(0L)(confirmations).toInt
      )
    }

  implicit val arbitrarySort: Arbitrary[Sort] =
    Arbitrary(Gen.oneOf(Sort.Ascending, Sort.Descending))

  private def confirmations(block: BlockView): Long = maxBlock - block.height.value

  private def mkInputs(outputs: List[OutputView]): Gen[(Satoshis, List[InputView])] = {
    val outputSum = outputs.foldMap(_.value)
    Gen
      .tailRecM[List[InputView], (Satoshis, List[InputView])](Nil) { acc =>
        inputView.arbitrary.map { i =>
          val newAcc  = i :: acc
          val feesOpt = newAcc.foldMap(_.value) - outputSum
          feesOpt.fold[Either[List[InputView], (Satoshis, List[InputView])]](Left(newAcc)) { fees =>
            Right((fees, newAcc))
          }
        }
      }
      .map { case (fees, inputs) =>
        val inputsWithValidIndices = inputs.zipWithIndex.map { case (input, idx) =>
          input.copy(inputIndex = idx)
        }
        (fees, inputsWithValidIndices)
      }
  }
}
