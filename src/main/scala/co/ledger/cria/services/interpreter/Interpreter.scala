package co.ledger.cria.services.interpreter

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.cria.models.interpreter.{AccountAddress, Action, BlockView, TransactionView}
import co.ledger.cria.clients.http.ExplorerClient
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.models.account.{Account, Coin}
import co.ledger.cria.models.interpreter._
import co.ledger.cria.utils.IOUtils
import doobie.Transactor
import fs2._

trait Interpreter {
  def saveTransactions(accountId: UUID)(implicit
      lc: CriaLogContext
  ): Pipe[IO, TransactionView, Unit]

  def removeDataFromCursor(
      accountId: UUID,
      blockHeightCursor: Option[Long],
      followUpId: UUID
  )(implicit lc: CriaLogContext): IO[Int]

  def getLastBlocks(accountId: UUID)(implicit lc: CriaLogContext): IO[List[BlockView]]

  def compute(
      account: Account,
      syncId: UUID,
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int]

}

class InterpreterImpl(
    explorer: Coin => ExplorerClient,
    db: Transactor[IO],
    maxConcurrent: Int,
    batchConcurrency: Db.BatchConcurrency
)(implicit cs: ContextShift[IO], t: Timer[IO])
    extends Interpreter
    with ContextLogging {

  val transactionService   = new TransactionService(db, maxConcurrent)
  val operationService     = new OperationService(db)
  val flaggingService      = new FlaggingService(db)
  val postSyncCheckService = new PostSyncCheckService(db)

  def saveTransactions(
      accountId: UUID
  )(implicit lc: CriaLogContext): Pipe[IO, TransactionView, Unit] = { transactions =>
    transactions
      .map(tx => AccountTxView(accountId, tx))
      .through(transactionService.saveTransactions)
      .void
  }

  def getLastBlocks(
      accountId: UUID
  )(implicit lc: CriaLogContext): IO[List[BlockView]] = {
    log.info(s"Getting last known blocks") *>
      transactionService
        .getLastBlocks(accountId)
        .compile
        .toList
  }

  def removeDataFromCursor(
      accountId: UUID,
      blockHeight: Option[Long],
      followUpId: UUID
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _     <- log.info(s"""Deleting data with parameters:
                      - blockHeight: $blockHeight""")
      txRes <- transactionService.removeFromCursor(accountId, blockHeight.getOrElse(0L))
      _     <- log.info(s"Deleted $txRes operations")
    } yield txRes
  }

  def compute(
      account: Account,
      syncId: UUID,
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _ <- log.info(s"Flagging belonging inputs and outputs")
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
      _ <- postSyncCheckService.check(account.id)
    } yield nbSavedOps
  }

  private def getAppropriateAction(
      account: Account,
      tx: TransactionAmounts
  )(implicit lc: CriaLogContext): IO[Action] =
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
      t: Timer[IO],
      lc: CriaLogContext
  ): Pipe[IO, Action, Int] = {

    val batchSize = Math.max(1000 / batchConcurrency.value, 100)

    in =>
      in.collect { case Save(tx) => tx }
        .flatMap(_.computeOperations)
        .chunkN(batchSize)
        .parEvalMap(batchConcurrency.value) { operations =>
          for {
            savedOps <- IOUtils.withTimer("Saving operations..")(
              operationService.saveOperations(operations.toList)
            )

            _ <- log.debug(
              s"$savedOps operations saved"
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
