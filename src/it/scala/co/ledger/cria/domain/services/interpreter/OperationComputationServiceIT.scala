package co.ledger.cria.domain.services.interpreter

import java.time.Instant
import fs2.Stream
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{
  AccountTxView,
  BlockHash,
  BlockHeight,
  BlockView,
  Coin,
  Derivation,
  InputView,
  Operation,
  OperationType,
  OutputView,
  SatoshisTestHelper,
  TransactionView,
  TransactionViewTestHelper
}
import co.ledger.cria.domain.models.{Sort, TxHash, keychain}
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import co.ledger.cria.itutils.ContainerSpec
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OperationComputationServiceIT
    extends AnyFlatSpec
    with ContainerSpec
    with Matchers
    with DefaultContextLogging {

  val accountUid = AccountUid("accountUid")
  val walletUid  = WalletUid("walletUid")

  private val outputAddress1 =
    AccountAddress(
      "1DtwACvd338XtHBFYJRVKRLxviD7YtYADa",
      ChangeType.External,
      Derivation(1, 0)
    )
  private val outputAddress2 =
    AccountAddress(
      "1LK8UbiRwUzC8KFEbMKvgbvriM9zLMce3C",
      ChangeType.Internal,
      Derivation(0, 0)
    )
  private val inputAddress =
    keychain.AccountAddress(
      "1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ",
      ChangeType.External,
      Derivation(1, 1)
    )

  private val sats80k = SatoshisTestHelper.unsafe(80000)

  private val sats50k = SatoshisTestHelper.unsafe(50000)

  private val sats9k = SatoshisTestHelper.unsafe(9434)

  private val sats21k = (sats80k - (sats50k + sats9k)).get

  private val time: Instant = Instant.parse("2019-04-04T10:03:22Z")

  val block: BlockView = BlockView(
    BlockHash.fromStringUnsafe("00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379"),
    BlockHeight.fromLongUnsafe(570153),
    time
  )

  val outputs = List(
    OutputView(0, sats50k, outputAddress1.accountAddress, "script", None, None),
    OutputView(1, sats9k, outputAddress2.accountAddress, "script", None, None)
  )
  val inputs = List(
    InputView(
      TxHash.fromStringUnsafe("0f38e5f1b12078495a9e80c6e0d77af3d674cfe6096bb6e7909993a53b6e8386"),
      0,
      0,
      sats80k,
      inputAddress.accountAddress,
      "script",
      List(),
      4294967295L,
      None
    )
  )

  val insertTx1: TransactionView =
    TransactionViewTestHelper.unsafe(
      "txId",
      TxHash.fromStringUnsafe("a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f"),
      time,
      0,
      sats21k,
      inputs,
      outputs,
      Some(block),
      1
    )

  val insertTx2: TransactionView = insertTx1.copy(
    id = "txId2",
    hash =
      TxHash.fromStringUnsafe("0f38e5f1b12078495a9e80c6e0d77af3d674cfe6096bb6e7909993a53b6e8386"),
    receivedAt = Instant.now()
  )

  val operationTx1 = Operation(
    Operation.uid(accountUid, insertTx1.hash, OperationType.Receive, Some(block.height)),
    accountUid,
    insertTx1.hash,
    insertTx1,
    OperationType.Receive,
    SatoshisTestHelper.unsafe(10),
    SatoshisTestHelper.unsafe(10),
    insertTx1.receivedAt,
    Some(block.height)
  )

  "getUncomputedOperations" should "not returned computed operations" in IOAssertion {
    setupDB *>
      testResources.use { tr =>
        val transactionRepository = tr.clients.persistenceFacade.transactionRecordRepository
        val computationService    = tr.clients.persistenceFacade.operationComputationService
        val operationRepository   = tr.clients.persistenceFacade.operationRepository
        val testUtils             = tr.testUtils

        for {
          _ <- testUtils.setupAccount(accountUid, walletUid)
          _ <- Stream
            .emits(Seq(AccountTxView(accountUid, insertTx1), AccountTxView(accountUid, insertTx2)))
            .through(transactionRepository.saveTransactions)
            .compile
            .toList

          firstResult <- computationService
            .getUncomputedOperations(accountUid, Sort.Ascending, None)
            .compile
            .toList

          _ <- operationRepository.saveBlocks(Coin.Btc, List(block))
          _ <- operationRepository.saveTransaction(Coin.Btc, accountUid, insertTx1)
          _ <- operationRepository.saveOperation(Coin.Btc, accountUid, walletUid, operationTx1)

          secondResult <- computationService
            .getUncomputedOperations(accountUid, Sort.Ascending, None)
            .compile
            .toList

        } yield {
          firstResult should have size 2
          assert(secondResult.nonEmpty)
          secondResult.map(_.hash) should contain(insertTx2.hash)
        }

      }
  }

}
