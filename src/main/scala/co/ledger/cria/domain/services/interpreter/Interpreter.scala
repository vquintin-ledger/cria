package co.ledger.cria.domain.services.interpreter

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.{IO, Timer}
import cats.implicits._
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.interpreter.{Action, BlockView, TransactionView}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.{Account, AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.AccountAddress
import co.ledger.cria.domain.services.ExplorerClient
import co.ledger.cria.utils.IOUtils
import fs2._

import scala.concurrent.duration.Duration

trait Interpreter {
  def saveTransactions(accountId: AccountUid)(implicit
      lc: CriaLogContext
  ): Pipe[IO, TransactionView, Unit]

  def removeDataFromCursor(
      accountId: AccountUid,
      blockHeight: Long
  )(implicit lc: CriaLogContext): IO[Int]

  def getLastBlocks(accountId: AccountUid)(implicit lc: CriaLogContext): IO[List[BlockView]]

  def compute(account: Account, walletUid: WalletUid)(
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int]

}

class InterpreterImpl(explorer: Coin => ExplorerClient, persistenceFacade: PersistenceFacade)(
    implicit t: Timer[IO]
) extends Interpreter
    with ContextLogging {

  private val transactionRecordRepository = persistenceFacade.transactionRecordRepository
  private val operationComputationService = persistenceFacade.operationComputationService
  private val operationRepository         = persistenceFacade.operationRepository

  private val UTXO_LOCKING_TIME = Duration("12 hours")

  def saveTransactions(
      accountId: AccountUid
  )(implicit lc: CriaLogContext): Pipe[IO, TransactionView, Unit] = { transactions =>
    transactions
      .map(tx => AccountTxView(accountId, tx))
      .through(transactionRecordRepository.saveTransactions)
      .void
  }

  //FIXME: Fix to work with WD db
  def getLastBlocks(
      accountId: AccountUid
  )(implicit lc: CriaLogContext): IO[List[BlockView]] = {
    log.info(s"Getting last known blocks") *>
      transactionRecordRepository
        .getLastBlocks(accountId)
        .compile
        .toList
  }

  def removeDataFromCursor(
      accountId: AccountUid,
      blockHeight: Long
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _     <- log.info(s"""Deleting data with parameters:
                      - blockHeight: $blockHeight""")
      txRes <- transactionRecordRepository.removeFromCursor(accountId, blockHeight)
      _     <- log.info(s"Deleted $txRes operations")
    } yield txRes
  }

  def compute(account: Account, walletUid: WalletUid)(
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _ <- log.info(s"Flagging belonging inputs and outputs")
      _ <- operationComputationService.flagInputsAndOutputs(account.accountUid, addresses)
      _ <- log.info(s"Computing operations")

      nbSavedOps <- IOUtils.withTimer("Computing finished")(
        computeOperations(account.accountUid, account.coin, walletUid).compile.foldMonoid
      )

      _ <- log.info(s"$nbSavedOps operations saved")

//      _ <- postSyncCheckService.check(account.accountUid)
    } yield nbSavedOps
  }

  private def computeOperations(accountId: AccountUid, coin: Coin, walletUid: WalletUid)(implicit
      lc: CriaLogContext
  ): Stream[IO, Int] =
    getUncomputedTxs(accountId)(200)
      .evalMap(
        _.traverse(getAction(coin, _))
      )
      .evalTap(deleteRejectedTransaction(accountId))
      .evalTap(saveWDBlocks(coin))
      .evalTap(saveWDTransactions(coin, accountId))
      .evalMap(saveWDOperations(coin, accountId, walletUid))
      .foldMonoid

  private def getAction(
      coin: Coin,
      op: Operation
  )(implicit lc: CriaLogContext): IO[Action] =
    op.transaction.block match {
      case Some(_) => IO.pure(Save(op))
      // don't check if a tx is still in the mempool if it's less than x hours old.
      case None if op.time.isAfter(Instant.now.minusSeconds(UTXO_LOCKING_TIME.toSeconds)) =>
        IO.pure(Save(op))
      case None =>
        explorer(coin).getTransaction(op.transaction.hash).map {
          // The transaction is till in the blockchain, we keep it
          case Some(_) => Save(op)
          // The transaction has been rejected by the network
          case None => Delete(op)
        }
    }

  private def getUncomputedTxs(accountId: AccountUid)(
      chunkSize: Int
  )(implicit
      lc: CriaLogContext
  ): Stream[IO, List[Operation]] = {
    val sort = Sort.Ascending
    operationComputationService
      .getUncomputedOperations(accountId, sort)
      .chunkN(chunkSize)
      .evalMap(chunk => getWDTxToSave(accountId, sort, chunk.toList))
  }

  private def getWDTxToSave(
      accountId: AccountUid,
      sort: Sort,
      uncomputedTransactions: List[TransactionAmounts]
  )(implicit lc: CriaLogContext): IO[List[Operation]] = {
    for {

      txToSaveMap <- NonEmptyList
        .fromList(uncomputedTransactions.map(_.hash))
        .map(hashNel =>
          operationComputationService
            .fetchTransactions(
              accountId,
              sort,
              hashNel
            )
            .map(tx => (tx.hash, tx))
            .compile
            .to(Map)
        )
        .getOrElse(IO.pure(Map.empty[TxHash, TransactionView]))

      operationMap = uncomputedTransactions.flatMap { opToSave =>
        val txView = txToSaveMap(opToSave.hash)
        opToSave.computeOperations(txView)
      }

    } yield operationMap
  }

  private def saveWDBlocks(
      coin: Coin
  )(actions: List[Action])(implicit lc: CriaLogContext): IO[Unit] =
    operationRepository.saveBlocks(
      coin,
      actions.collect { case Save(op) => op.transaction.block }.flatten.distinct
    )

  private def saveWDTransactions(coin: Coin, accountUid: AccountUid)(
      actions: List[Action]
  )(implicit lc: CriaLogContext): IO[Unit] = {
    val txsToSave = actions.collect { case Save(a) => a.transaction }.distinct

    log.info(s"Saving ${txsToSave.size} WD transactions") *>
      txsToSave.traverse { a =>
        operationRepository.saveTransaction(coin, accountUid, a)
      }.void
  }

  private def saveWDOperations(coin: Coin, accountUid: AccountUid, walletUid: WalletUid)(
      actions: List[Action]
  )(implicit lc: CriaLogContext): IO[Int] = {
    val opsToSave = actions.collect { case Save(a) => a }

    log.info(s"Saving ${opsToSave.size} WD Operations") *>
      opsToSave
        .traverse(ops => operationRepository.saveOperation(coin, accountUid, walletUid, ops))
        .map(_.sum)
  }

  private def deleteRejectedTransaction(accountUid: AccountUid)(
      actions: List[Action]
  )(implicit lc: CriaLogContext): IO[Int] =
    actions
      .collect { case Delete(op) =>
        log.info(
          s"Deleting unconfirmed transaction from db : ${op.transaction.hash} (not in explorer)"
        ) *>
          operationRepository.deleteRejectedTransaction(accountUid, op.transaction.hash)
      }
      .sequence
      .map(_.sum)

}
