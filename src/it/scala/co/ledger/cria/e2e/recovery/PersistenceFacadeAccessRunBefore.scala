package co.ledger.cria.e2e.recovery

import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.AccountAddress
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.services.interpreter._
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

final class PersistenceFacadeAccessRunBefore(persistenceFacade: PersistenceFacade, action: IO[Unit])
    extends PersistenceFacade {
  override def transactionRecordRepository: TransactionRecordRepository =
    new TransactionRecordRepository {
      val delegate = persistenceFacade.transactionRecordRepository

      override def saveTransactions(implicit lc: CriaLogContext): Pipe[IO, AccountTxView, Unit] =
        doBeforePipe(delegate.saveTransactions)

      override def removeFromCursor(accountId: AccountUid, blockHeight: BlockHeight): IO[Int] =
        doBeforeAction(delegate.removeFromCursor(accountId, blockHeight))

      override def getLastBlocks(accountId: AccountUid): fs2.Stream[IO, BlockView] =
        doBeforeStream(delegate.getLastBlocks(accountId))
    }

  override def operationComputationService: OperationComputationService =
    new OperationComputationService {
      val delegate = persistenceFacade.operationComputationService

      override def flagInputsAndOutputs(
          accountId: AccountUid,
          accountAddresses: List[AccountAddress]
      ): IO[Unit] =
        doBeforeAction(delegate.flagInputsAndOutputs(accountId, accountAddresses))

      override def getUncomputedOperations(
          accountId: AccountUid,
          sort: Sort,
          fromBlockHeight: Option[BlockHeight]
      ): fs2.Stream[IO, TransactionAmounts] =
        doBeforeStream(delegate.getUncomputedOperations(accountId, sort, fromBlockHeight))

      override def fetchTransactions(
          accountId: AccountUid,
          sort: Sort,
          hashes: NonEmptyList[TxHash]
      ): fs2.Stream[IO, TransactionView] =
        doBeforeStream(delegate.fetchTransactions(accountId, sort, hashes))
    }

  override def postSyncCheckService: PostSyncCheckService =
    new PostSyncCheckService {
      override def check(accountId: AccountUid): IO[Unit] =
        doBeforeAction(persistenceFacade.postSyncCheckService.check(accountId))
    }

  override def operationRepository: OperationRepository =
    new OperationRepository {
      val delegate = persistenceFacade.operationRepository

      override def saveOperation(
          coin: Coin,
          accountUid: AccountUid,
          walletUid: WalletUid,
          op: Operation
      ): IO[Int] =
        doBeforeAction(delegate.saveOperation(coin, accountUid, walletUid, op))

      override def saveTransaction(
          coin: Coin,
          accountUid: AccountUid,
          transactionView: TransactionView
      ): IO[Unit] =
        doBeforeAction(delegate.saveTransaction(coin, accountUid, transactionView))

      override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit
          lc: CriaLogContext
      ): IO[Unit] =
        doBeforeAction(delegate.saveBlocks(coin, blocks))

      override def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): IO[Int] =
        doBeforeAction(delegate.deleteRejectedTransaction(accountId, hash))
    }

  private def doBeforeAction[A](after: IO[A]): IO[A] =
    action >> after

  private def doBeforeStream[A](after: fs2.Stream[IO, A]): fs2.Stream[IO, A] =
    fs2.Stream.eval(action) >> after

  private def doBeforePipe[I, O](after: fs2.Pipe[IO, I, O]): fs2.Pipe[IO, I, O] =
    (s: fs2.Stream[IO, I]) => doBeforeStream[O](after(s))
}
