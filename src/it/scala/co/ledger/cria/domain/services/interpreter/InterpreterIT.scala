package co.ledger.cria.domain.services.interpreter

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.cria.domain.mocks.ExplorerClientMock
import co.ledger.cria.itutils.ContainerSpec
import co.ledger.cria.utils.IOAssertion
import co.ledger.cria.logging.CriaLogContext
import co.ledger.cria.domain.models.{Sort, TxHash, keychain}
import co.ledger.cria.domain.models.account.{Account, AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{
  AccountTxView,
  BlockHash,
  BlockView,
  Coin,
  InputView,
  OutputView,
  TransactionView
}
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import co.ledger.cria.itutils.models.GetUtxosResult
import org.scalatest.matchers.should.Matchers
import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpec

class InterpreterIT extends AnyFlatSpec with ContainerSpec with Matchers {

  val explorer = new ExplorerClientMock()

  val account: Account =
    Account(
      AccountUid(UUID.randomUUID().toString),
      KeychainId.fromString("b723c553-3a9a-4130-8883-ee2f6c2f9202").get,
      Coin.Btc
    )
  val accountUid: AccountUid = account.accountUid
  val walletUid: WalletUid   = WalletUid(UUID.randomUUID().toString)

  override implicit val lc: CriaLogContext = CriaLogContext().withAccountId(accountUid)

  private val outputAddress1 =
    AccountAddress("1DtwACvd338XtHBFYJRVKRLxviD7YtYADa", ChangeType.External, NonEmptyList.of(1, 0))
  private val outputAddress2 =
    AccountAddress(
      "1LK8UbiRwUzC8KFEbMKvgbvriM9zLMce3C",
      ChangeType.Internal,
      NonEmptyList.of(0, 0)
    )
  private val inputAddress =
    keychain.AccountAddress(
      "1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ",
      ChangeType.External,
      NonEmptyList.of(1, 1)
    )

  private val time: Instant = Instant.parse("2019-04-04T10:03:22Z")

  val block: BlockView = BlockView(
    BlockHash.fromStringUnsafe("00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379"),
    570153,
    time
  )

  val outputs = List(
    OutputView(0, 50000, outputAddress1.accountAddress, "script", None, None),
    OutputView(1, 9434, outputAddress2.accountAddress, "script", None, None)
  )
  val inputs = List(
    InputView(
      "0f38e5f1b12078495a9e80c6e0d77af3d674cfe6096bb6e7909993a53b6e8386",
      0,
      0,
      80000,
      inputAddress.accountAddress,
      "script",
      List(),
      4294967295L,
      None
    )
  )

  val coinbaseTx: TransactionView =
    TransactionView(
      "txId",
      TxHash.fromStringUnsafe("0f38e5f1b12078495a9e80c6e0d77af3d674cfe6096bb6e7909993a53b6e8386"),
      time,
      0,
      0,
      Nil,
      List(OutputView(0, 80000, inputAddress.accountAddress, "script", None, None)),
      Some(block),
      1
    )

  val insertTx: TransactionView =
    TransactionView(
      "txId",
      TxHash.fromStringUnsafe("a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f"),
      time,
      0,
      20566,
      inputs,
      outputs,
      Some(block),
      1
    )

  def saveTxs(interpreter: Interpreter, txs: List[TransactionView]): IO[Unit] =
    Stream
      .emits(txs)
      .through(interpreter.saveTransactions(accountUid))
      .compile
      .drain

  "a transaction" should "have a full lifecycle" in IOAssertion {
    setupDB *>
      testResources.use { tr =>
        val interpreter =
          new InterpreterImpl(_ => explorer, tr.clients.persistenceFacade)

        val utils = tr.testUtils

        val block1 = BlockView(
          BlockHash.fromStringUnsafe(
            "0000000000000000000cc9cc204cf3b314d106e69afbea68f2ae7f9e5047ba74"
          ),
          block.height + 1,
          time
        )

        // intentionally disordered
        val blocksToSave = List(block1, block)

        explorer.addToBC(coinbaseTx)
        explorer.addToBC(insertTx)

        for {
          _ <- utils.setupAccount(accountUid, walletUid)
          _ <- saveTxs(
            interpreter,
            List(
              coinbaseTx,
              insertTx.copy(
                hash = TxHash.fromStringUnsafe(
                  "1c66772c25c49d2baf9b2ca04aa72eea9cb998dc7dc66c0a704948d20a197349"
                ),
                block = Some(block1)
              )
            )
          )

          _ <- interpreter.compute(account, walletUid)(
            List(inputAddress, outputAddress2)
          )

          blocks                 <- interpreter.getLastBlocks(accountUid)
          opsBeforeDeletionTotal <- utils.getOperationCount(accountUid)
          resUtxoBeforeDeletion <- utils.getUtxos(
            accountUid,
            20,
            0,
            Sort.Ascending
          )
          GetUtxosResult(utxosBeforeDeletion, utxosBeforeDeletionTotal, utxosBeforeDeletionTrunc) =
            resUtxoBeforeDeletion

          _ <- interpreter.removeDataFromCursor(
            accountUid,
            block.height
          )

          opsAfterDeletionTotal <- utils.getOperationCount(accountUid)

          blocksAfterDelete <- interpreter.getLastBlocks(accountUid)
        } yield {
          withClue(s"${blocksToSave.size} blocks should be saved, found: ${blocks.size}") {
            blocks should be(blocksToSave.sortBy(_.height)(Ordering[Long].reverse))
          }
          withClue(s"There should be 2 ops before delete") {
            opsBeforeDeletionTotal shouldBe 2
          }
          withClue(s"There should be 1 utxo before delete") {
            utxosBeforeDeletion should have size 1
            utxosBeforeDeletionTotal shouldBe 1
            utxosBeforeDeletionTrunc shouldBe false
          }
          withClue(s"There should be no ops after delete") {
            opsAfterDeletionTotal shouldBe 0
          }
          withClue(s"There should be no block after delete") {
            blocksAfterDelete shouldBe empty
          }
        }
      }
  }

  "an unconfirmed transaction" should "have a full lifecycle" in IOAssertion {
    setupDB *>
      testResources.use { tr =>
        val interpreter =
          new InterpreterImpl(_ => explorer, tr.clients.persistenceFacade)

        val utils = tr.testUtils

        val uTx = TransactionView(
          "txId",
          TxHash.fromStringUnsafe(
            "a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f"
          ),
          time,
          0,
          20566,
          inputs,
          outputs,
          None,
          1
        )
        val uTx2 = uTx.copy(
          id = "txId2",
          hash = TxHash.fromStringUnsafe(
            "a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1e"
          )
        )

        explorer.addToBC(uTx)
        explorer.addToBC(uTx2)

        for {
          _ <- utils.setupAccount(accountUid, walletUid)
          _ <- saveTxs(interpreter, List(uTx, uTx2))

          _ <- interpreter.compute(account, walletUid)(
            List(outputAddress1)
          )
          operationCount <- utils.getOperationCount(accountUid)
          currentBalance <- utils.getBalance(accountUid)
        } yield {
          currentBalance.balance should be(0)
          currentBalance.unconfirmedBalance should be(100000)

          operationCount shouldBe 2
//          operations.head.blockHeight should be(None)

        }
      }

  }

  "an unconfirmed transaction" should "be updated if it's been mined" in IOAssertion {
    setupDB *>
      testResources.use { tr =>
        val interpreter =
          new InterpreterImpl(_ => explorer, tr.clients.persistenceFacade)

        val utils = tr.testUtils

        val uTx = TransactionView(
          "txId",
          TxHash.fromStringUnsafe(
            "a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f"
          ),
          time,
          0,
          20566,
          inputs,
          outputs,
          None,
          1
        )
        explorer.addToBC(uTx)

        val tx = TransactionView(
          "txId",
          TxHash.fromStringUnsafe(
            "a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f"
          ),
          time,
          0,
          20566,
          inputs,
          outputs,
          Some(block),
          1
        )

        for {
          _ <- utils.setupAccount(accountUid, walletUid)
          _ <- saveTxs(interpreter, List(uTx, tx))

          _ <- interpreter.compute(account, walletUid)(
            List(outputAddress1)
          )
          operationCount <- utils.getOperationCount(accountUid)
        } yield {
          operationCount shouldBe 1
        }
      }

  }

  "an account" should "go through multiple cycles" in IOAssertion {
    setupDB *>
      testResources.use { tr =>
        val interpreter =
          new InterpreterImpl(_ => explorer, tr.clients.persistenceFacade)

        val utils = tr.testUtils

        val uTx1 = TransactionView(
          "tx1",
          TxHash.fromStringUnsafe(
            "b23ed6a7362a45ed880d29aa601baed2ee718b440a562daf31473a65fc99d0c7"
          ),
          time,
          0,
          1000,
          List(
            InputView(".", 0, 0, 101000, inputAddress.accountAddress, "script", List(), 0L, None)
          ),
          List(
            OutputView(0, 100000, outputAddress1.accountAddress, "script", None, None)
          ), // receive
          None,
          1
        )
        explorer.addToBC(uTx1)

        val uTx2 = uTx1.copy(
          id = "tx2",
          hash = TxHash.fromStringUnsafe(
            "8f6ffe55e14c78025c9ee5e9a31f3562dedb6d36157c7cc1eda9dae7b6b25a7e"
          ),
          outputs = List(
            OutputView(0, 5000, outputAddress2.accountAddress, "script", None, None)
          ) // receive
        )
        explorer.addToBC(uTx2)

        val uTx3 = uTx1.copy(
          id = "tx3",
          hash = TxHash.fromStringUnsafe(
            "ab01bc59e0174a4f53a8c87e3ddd0d55a25ad207dd7d1d1cdf6efd651e43a391"
          ),
          inputs = List(
            InputView(
              "tx1",
              0,
              0,
              100000,
              outputAddress1.accountAddress,
              "script",
              List(),
              0L,
              None
            ) // send utxo from tx1
          ),
          outputs = List(OutputView(0, 99000, inputAddress.accountAddress, "script", None, None))
        )
        explorer.addToBC(uTx3)

        for {
          _                   <- utils.setupAccount(accountUid, walletUid)
          _                   <- saveTxs(interpreter, List(uTx1))
          _                   <- interpreter.compute(account, walletUid)(List(outputAddress1))
          firstBalance        <- utils.getBalance(accountUid)
          firstOperationCount <- utils.getOperationCount(accountUid)

          _ <- saveTxs(
            interpreter,
            List(
              uTx1.copy(block =
                Some(
                  BlockView(
                    BlockHash.fromStringUnsafe(
                      "90a9c00424e06c9074ed6e70a33005046767682c2a077e1b9ee02a3adc336e9f"
                    ),
                    1L,
                    time
                  )
                )
              ), // mine first transaction
              uTx2
            )
          )
          _ <- interpreter.compute(account, walletUid)(
            List(outputAddress1, outputAddress2)
          )
          secondBalance        <- utils.getBalance(accountUid)
          secondOperationCount <- utils.getOperationCount(accountUid)

          _ <- saveTxs(
            interpreter,
            List(
              uTx2.copy(block =
                Some(
                  BlockView(
                    BlockHash.fromStringUnsafe(
                      "cbbeeea984ca073ef0275d74b15cc581dd12e608bd60ae7964f32bb37771848f"
                    ),
                    2L,
                    time.plus(10, ChronoUnit.MINUTES)
                  )
                )
              ), // mine second transaction
              uTx3
            )
          )
          _ <- interpreter.compute(account, walletUid)(
            List(outputAddress1, outputAddress2)
          )
          lastBalance        <- utils.getBalance(accountUid)
          lastOperationCount <- utils.getOperationCount(accountUid)

        } yield {

          firstOperationCount shouldBe 1
          firstBalance.balance shouldBe 0
          firstBalance.unconfirmedBalance shouldBe 100000
          secondOperationCount shouldBe 2
          secondBalance.balance shouldBe 100000
          secondBalance.unconfirmedBalance shouldBe 5000;
          lastOperationCount shouldBe 3
          lastBalance.balance shouldBe 105000
          lastBalance.unconfirmedBalance shouldBe -100000
        }
      }

  }

  "A transaction rejected by the network" should "not stay in db" in IOAssertion {
    setupDB *>
      testResources.use { tr =>
        val interpreter =
          new InterpreterImpl(_ => explorer, tr.clients.persistenceFacade)

        val utils = tr.testUtils

        val uTx1 = AccountTxView(
          accountUid,
          TransactionView(
            "utx1",
            TxHash.fromStringUnsafe(
              "a99c7e333b287e7ecb017b33b7faf028a7eba69ae716114401e398f568f6ad9b"
            ),
            time,
            0,
            1000,
            List(
              InputView(".", 0, 0, 101000, inputAddress.accountAddress, "script", List(), 0L, None)
            ),
            List(
              OutputView(0, 100000, outputAddress1.accountAddress, "script", None, None)
            ),
            None,
            1
          )
        )

        // We add the transaction to the explorer
        explorer.addToBC(uTx1.tx)

        for {
          _ <- utils.setupAccount(accountUid, walletUid)
          _ <- Stream
            .emit(uTx1.tx)
            .through(interpreter.saveTransactions(uTx1.accountId))
            .compile
            .drain
          _     <- interpreter.compute(account, walletUid)(List(outputAddress1))
          first <- utils.getOperationCount(accountUid)
          _ <- Stream
            .emit(uTx1.tx)
            .through(interpreter.saveTransactions(uTx1.accountId))
            .compile
            .drain
          _      <- interpreter.compute(account, walletUid)(List(outputAddress1))
          second <- utils.getOperationCount(accountUid)
          // The third time the transaction is not saved again, meaning that it disapeared from the network without being mined.
          // We now remove the transaction from the explorer
          _ <- Stream
            .emit(
              uTx1.tx.copy(receivedAt = Instant.now.minus(24, ChronoUnit.HOURS))
            )
            .through(interpreter.saveTransactions(uTx1.accountId))
            .compile
            .drain
          _ = explorer.removeFromBC(uTx1.tx.hash)
          _     <- interpreter.compute(account, walletUid)(List(outputAddress1))
          third <- utils.getOperationCount(accountUid)
        } yield {
          withClue("On first sync, tx should be present") {
            first shouldBe 1
          }
          withClue("On second sync, tx should still be present") {
            second shouldBe 1
          }
          withClue("On third sync, tx should have disappeared") {
            third shouldBe 0
          }
        }

      }
  }
}
