package co.ledger.lama.bitcoin.interpreter

import cats.effect.{Clock, ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.interpreter.Config.Db
import co.ledger.lama.bitcoin.interpreter.services._
import co.ledger.lama.common.logging.{ContextLogging, LamaLogContext}
import co.ledger.lama.common.models._
import fs2._
import doobie.Transactor
import java.util.UUID
import java.util.concurrent.TimeUnit

import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient
import co.ledger.lama.bitcoin.interpreter.models.{
  AccountTxView,
  Action,
  Delete,
  Save,
  TransactionAmounts
}

class Interpreter(
    explorer: Coin => ExplorerClient,
    db: Transactor[IO],
    maxConcurrent: Int,
    batchConcurrency: Db.BatchConcurrency
)(implicit cs: ContextShift[IO], clock: Clock[IO])
    extends ContextLogging {

  val transactionService = new TransactionService(db, maxConcurrent)
  val operationService   = new OperationService(db)
  val flaggingService    = new FlaggingService(db)

  def saveTransactions: Pipe[IO, AccountTxView, Int] =
    transactionService.saveTransactions

  def getLastBlocks(
      accountId: UUID
  ): IO[List[BlockView]] = {
    implicit val lc: LamaLogContext = LamaLogContext().withAccountId(accountId)

    log.info(s"Getting last known blocks") *>
      transactionService
        .getLastBlocks(accountId)
        .compile
        .toList
  }

  def removeDataFromCursor(
      accountId: UUID,
      blockHeight: Long,
      followUpId: UUID
  ): IO[Int] = {
    implicit val lc: LamaLogContext =
      LamaLogContext().withAccountId(accountId).withFollowUpId(followUpId)

    for {
      _     <- log.info(s"""Deleting data with parameters:
                      - blockHeight: $blockHeight""")
      txRes <- transactionService.removeFromCursor(accountId, blockHeight)
      _     <- log.info(s"Deleted $txRes operations")
    } yield txRes
  }

  def compute(
      account: Account,
      syncId: UUID,
      addresses: List[AccountAddress]
  ): IO[Int] = {
    implicit val lc: LamaLogContext = LamaLogContext().withAccount(account).withFollowUpId(syncId)

    for {
      _ <- log.info(s"Flagging inputs and outputs belong")
      _ <- flaggingService.flagInputsAndOutputs(account.id, addresses)
      _ <- operationService.deleteUnconfirmedOperations(account.id)

      _ <- log.info(s"Computing operations")

      nbSavedOps <- operationService
        .getUncomputedOperations(account.id)
        .evalMap(tx => getAppropriateAction(account, tx))
        .broadcastThrough(
          saveOperationPipe,
          deleteRejectedTransactionPipe
        )
        .compile
        .foldMonoid
      _ <- log.info(s"$nbSavedOps operations saved")

    } yield nbSavedOps
  }

  private def getAppropriateAction(
      account: Account,
      tx: TransactionAmounts
  )(implicit lc: LamaLogContext): IO[Action] =
    tx.blockHeight match {
      case Some(_) => IO.pure(Save(tx))
      case None =>
        explorer(account.coin).getTransaction(tx.hash).map {
          case Some(_) => Save(tx)
          case None    => Delete(tx)
        }
    }

  private def saveOperationPipe(implicit
      cs: ContextShift[IO],
      clock: Clock[IO],
      lc: LamaLogContext
  ): Pipe[IO, Action, Int] = {

    val batchSize = Math.max(1000 / batchConcurrency.value, 100)

    in =>
      in.collect { case Save(tx) => tx }
        .flatMap(_.computeOperations)
        .chunkN(batchSize)
        .parEvalMap(batchConcurrency.value) { operations =>
          for {
            start    <- clock.monotonic(TimeUnit.MILLISECONDS)
            savedOps <- operationService.saveOperations(operations.toList)
            end      <- clock.monotonic(TimeUnit.MILLISECONDS)
            _ <- log.debug(
              s"${operations.head.map(_.uid)}: $savedOps operations saved in ${end - start} ms"
            )
          } yield Chunk(operations.size)

        }
        .flatMap(Stream.chunk)
  }

  private def deleteRejectedTransactionPipe: Pipe[IO, Action, Int] = { stream =>
    stream
      .collect { case Delete(tx) => tx }
      .evalMap { tx =>
        transactionService.deleteUnconfirmedTransaction(tx.accountId, tx.hash) *> IO.pure(1)
      }
  }

}
