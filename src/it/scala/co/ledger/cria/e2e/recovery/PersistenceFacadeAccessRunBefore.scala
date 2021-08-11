package co.ledger.cria.e2e.recovery

import cats.Monad
import cats.data.NonEmptyList
import cats.implicits._
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.AccountAddress
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.services.interpreter._
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

final class PersistenceFacadeAccessRunBefore[F[_]](persistenceFacade: PersistenceFacade[F], action: F[Unit])(implicit F: Monad[F])
    extends PersistenceFacade[F] {
  override def transactionRecordRepository: TransactionRecordRepository[F] =
    new TransactionRecordRepository[F] {
      val delegate = persistenceFacade.transactionRecordRepository

      override def saveTransactions(implicit lc: CriaLogContext): Pipe[F, AccountTxView, Unit] =
        doBeforePipe(delegate.saveTransactions)

      override def removeFromCursor(accountId: AccountUid, blockHeight: BlockHeight): F[Int] =
        doBeforeAction(delegate.removeFromCursor(accountId, blockHeight))

      override def getLastBlocks(accountId: AccountUid): fs2.Stream[F, BlockView] =
        doBeforeStream(delegate.getLastBlocks(accountId))

      override def getLastBlockHash(accountId: AccountUid): F[Option[BlockHash]] =
        doBeforeAction(delegate.getLastBlockHash(accountId))
    }

  override def operationComputationService: OperationComputationService[F] =
    new OperationComputationService[F] {
      val delegate = persistenceFacade.operationComputationService

      override def flagInputsAndOutputs(
          accountId: AccountUid,
          accountAddresses: List[AccountAddress]
      ): F[Unit] =
        doBeforeAction(delegate.flagInputsAndOutputs(accountId, accountAddresses))

      override def getUncomputedOperations(
          accountId: AccountUid,
          sort: Sort,
          fromBlockHeight: Option[BlockHeight]
      ): fs2.Stream[F, TransactionAmounts] =
        doBeforeStream(delegate.getUncomputedOperations(accountId, sort, fromBlockHeight))

      override def fetchTransactions(
          accountId: AccountUid,
          sort: Sort,
          hashes: NonEmptyList[TxHash]
      ): fs2.Stream[F, TransactionView] =
        doBeforeStream(delegate.fetchTransactions(accountId, sort, hashes))
    }

  override def postSyncCheckService: PostSyncCheckService[F] =
    new PostSyncCheckService[F] {
      override def check(accountId: AccountUid): F[Unit] =
        doBeforeAction(persistenceFacade.postSyncCheckService.check(accountId))
    }

  override def operationRepository: OperationRepository[F] =
    new OperationRepository[F] {
      val delegate = persistenceFacade.operationRepository

      override def saveOperation(
          coin: Coin,
          accountUid: AccountUid,
          walletUid: WalletUid,
          op: Operation
      ): F[Int] =
        doBeforeAction(delegate.saveOperation(coin, accountUid, walletUid, op))

      override def saveTransaction(
          coin: Coin,
          accountUid: AccountUid,
          transactionView: TransactionView
      ): F[Unit] =
        doBeforeAction(delegate.saveTransaction(coin, accountUid, transactionView))

      override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit
          lc: CriaLogContext
      ): F[Unit] =
        doBeforeAction(delegate.saveBlocks(coin, blocks))

      override def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): F[Int] =
        doBeforeAction(delegate.deleteRejectedTransaction(accountId, hash))
    }

  private def doBeforeAction[A](after: F[A]): F[A] =
    action >> after

  private def doBeforeStream[A](after: fs2.Stream[F, A]): fs2.Stream[F, A] =
    fs2.Stream.eval(action) >> after

  private def doBeforePipe[I, O](after: fs2.Pipe[F, I, O]): fs2.Pipe[F, I, O] =
    (s: fs2.Stream[F, I]) => doBeforeStream[O](after(s))
}
