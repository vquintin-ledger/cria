package co.ledger.cria.domain.services.interpreter

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.cria.domain.adapters.wd.models.{WDBlock, WDOperation, WDTransaction, WDTxToSave}
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.interpreter.{Action, BlockView, TransactionView}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.{Account, AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.AccountAddress
import co.ledger.cria.domain.services.ExplorerClient
import co.ledger.cria.utils.IOUtils
import doobie.Transactor
import fs2._

trait Interpreter {
  def saveTransactions(accountId: AccountUid)(implicit
      lc: CriaLogContext
  ): Pipe[IO, TransactionView, Unit]

  def removeDataFromCursor(
      accountId: AccountUid,
      blockHeight: Option[Long]
  )(implicit lc: CriaLogContext): IO[Int]

  def getLastBlocks(accountId: AccountUid)(implicit lc: CriaLogContext): IO[List[BlockView]]

  def compute(account: Account, walletUid: WalletUid)(
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int]

}

class InterpreterImpl(
    explorer: Coin => ExplorerClient,
    db: Transactor[IO],
    flaggingService: FlaggingService,
    transactionService: TransactionService,
    operationService: OperationService)(implicit cs: ContextShift[IO], t: Timer[IO])
    extends Interpreter
    with ContextLogging {

  val wdService            = new WDService(db)
  val postSyncCheckService = new PostSyncCheckService(db)

  def saveTransactions(
      accountId: AccountUid
  )(implicit lc: CriaLogContext): Pipe[IO, TransactionView, Unit] = { transactions =>
    transactions
      .map(tx => AccountTxView(accountId, tx))
      .through(transactionService.saveTransactions)
      .void
  }

  //TODO: Fix to work with WD db
  def getLastBlocks(
      accountId: AccountUid
  )(implicit lc: CriaLogContext): IO[List[BlockView]] = {
    log.info(s"Getting last known blocks") *>
      transactionService
        .getLastBlocks(accountId)
        .compile
        .toList
  }

  def removeDataFromCursor(
      accountId: AccountUid,
      blockHeight: Option[Long]
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _ <- log.info(s"""Deleting data with parameters:
                      - blockHeight: $blockHeight""")
//      txRes <- transactionService.removeFromCursor(accountId, blockHeight.getOrElse(0L))
      txRes <- wdService.removeFromCursor(blockHeight)
      _     <- log.info(s"Deleted $txRes operations")
    } yield txRes
  }

  def compute(account: Account, walletUid: WalletUid)(
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int] = {
    for {
      _ <- log.info(s"Flagging belonging inputs and outputs")
      _ <- flaggingService.flagInputsAndOutputs(account.accountUid, addresses)
//      _ <- operationService.deleteUnconfirmedOperations(account.id)

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
    getUncomputedTxs(accountId, coin, walletUid)(200)
      .evalMap(
        _.traverse(getAction(coin, _))
      )
//      .evalTap(deleteRejectedTransaction)
      .evalTap(saveWDBlocks)
      .evalTap(saveWDTransactions)
      .evalMap(saveWDOperations)
      .foldMonoid

  private def getAction(
      coin: Coin,
      tx: WDTxToSave
  )(implicit lc: CriaLogContext): IO[Action] =
    tx.block match {
      case Some(_) => IO.pure(Save(tx))
      case None =>
        explorer(coin).getTransaction(TxHash.fromStringUnsafe(tx.tx.hash)).map {
          case Some(_) => Save(tx)
          case None    => Delete(tx)
        }
    }

  private def getUncomputedTxs(accountId: AccountUid, coin: Coin, walletUid: WalletUid)(
      chunkSize: Int
  )(implicit
      lc: CriaLogContext
  ): Stream[IO, List[WDTxToSave]] = {
    val sort = Sort.Ascending
    operationService
      .getUncomputedOperations(accountId, sort)
      .chunkN(chunkSize)
      .evalMap(chunk => getWDTxToSave(accountId, coin, walletUid, sort, chunk.toList))
  }

  private def getWDTxToSave(
      accountId: AccountUid,
      coin: Coin,
      walletUid: WalletUid,
      sort: Sort,
      uncomputedTransactions: List[TransactionAmounts]
  )(implicit lc: CriaLogContext): IO[List[WDTxToSave]] = {
    for {

      txToSaveMap <- NonEmptyList
        .fromList(uncomputedTransactions.map(_.hash))
        .map(hashNel =>
          transactionService
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

      operationMap = uncomputedTransactions.map { opToSave =>
        val txView = txToSaveMap(opToSave.hash)
        val block  = txView.block.map(WDBlock.fromBlock(_, coin))
        val wdTx   = WDTransaction.fromTransactionView(accountId, txView, block)
        val ops =
          opToSave.computeOperations.map(
            WDOperation.fromOperation(_, coin, wdTx, txView, walletUid)
          )
        WDTxToSave(block, wdTx, ops)
      }

    } yield operationMap
  }

  private def saveWDBlocks(actions: List[Action])(implicit lc: CriaLogContext): IO[Int] =
    wdService.saveBlocks(actions.collect { case Save(WDTxToSave(Some(block), _, _)) =>
      block
    }.distinct)

  private def saveWDTransactions(actions: List[Action])(implicit lc: CriaLogContext): IO[Int] = {
    val txsToSave = actions
      .collect { case Save(a) => a }

    log.info(s"Saving ${txsToSave.size} WD transactions") *>
      txsToSave
        .map { a =>
          wdService.saveTransaction(a.tx)
        }
        .sequence
        .map(_.sum)
  }

  private def saveWDOperations(actions: List[Action])(implicit lc: CriaLogContext): IO[Int] = {
    val opsToSave = actions
      .flatMap {
        case Save(a) => a.ops
        case _       => Nil
      }

    log.info(s"Saving ${opsToSave.size} WD Operations") *>
      opsToSave
        .map(wdService.saveWDOperation)
        .sequence
        .map(_.sum)
  }

//  private def deleteRejectedTransaction(actions: List[Action])(implicit
//      lc: CriaLogContext
//  ): IO[Int] =
//    actions
//      .collect { case Delete(tx) =>
//        // TODO: remove from WD instead
//        log.info(s"Deleting unconfirmed transaction from db : ${tx.tx.hash} (not in explorer)") *>
//          wdService.deleteRejectedTransaction(tx.tx.hash)
//      }
//      .sequence
//      .map(_.sum)

}
