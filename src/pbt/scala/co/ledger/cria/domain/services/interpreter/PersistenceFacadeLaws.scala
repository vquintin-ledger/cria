package co.ledger.cria.domain.services.interpreter

import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockHeight, BlockView, Satoshis, TransactionView}
import cats.Monad
import cats.data.NonEmptyList
import cats.implicits._
import co.ledger.cria.logging.DefaultContextLogging
import cats.kernel.laws.{IsEq, IsEqArrow}
import co.ledger.cria.domain.models.keychain.ChangeType
import co.ledger.cria.domain.models.{Sort, TxHash}
import fs2.Stream

trait PersistenceFacadeLaws[F[_]] extends DefaultContextLogging {
  implicit def F: Monad[F]

  implicit def fs2Compiler: fs2.Stream.Compiler[F, F]

  def persistenceFacade: PersistenceFacade[F]

  private def transactionRecordRepository = persistenceFacade.transactionRecordRepository

  def noMoreTransactionsAfterRemove(
      transactions: List[TransactionView],
      accountUid: AccountUid,
      blockHeight: BlockHeight
  ): IsEq[F[Boolean]] = {
    val save: F[Unit] =
      fs2.Stream
        .emits(transactions)
        .map(t => AccountTxView(accountUid, t))
        .through(transactionRecordRepository.saveTransactions)
        .compile
        .drain

    val read: F[List[BlockView]] =
      transactionRecordRepository.getLastBlocks(accountUid).compile.toList

    assert {
      for {
        _      <- save
        _      <- transactionRecordRepository.removeFromCursor(accountUid, blockHeight)
        blocks <- read
      } yield blocks.forall(b => b.height < blockHeight)
    }
  }

  def canReadSavedTransaction(
      accountUid: AccountUid,
      txView: TransactionView,
      sort: Sort
  ): IsEq[F[List[TransactionView]]] = {
    val prgm =
      for {
        _         <- saveTransaction(accountUid, txView)
        readAgain <- getTransactions(accountUid, txView.hash, sort)
      } yield readAgain

    prgm <-> F.pure(List(txView))
  }

  def transactionIsUpdatedWhenItDoesNotHaveABlock(
      accountUid: AccountUid,
      txView: TransactionView,
      blockView: BlockView,
      sort: Sort
  ): IsEq[F[List[TransactionView]]] = {
    val noBlockTx = txView.copy(block = None)
    val updatedTx = txView.copy(block = Some(blockView))

    val prgm =
      for {
        _         <- saveTransaction(accountUid, noBlockTx)
        _         <- saveTransaction(accountUid, updatedTx)
        readAgain <- getTransactions(accountUid, txView.hash, sort)
      } yield readAgain

    prgm <-> F.pure(List(updatedTx))
  }

  def transactionIsUpdatedWhenItHasABlock(
      accountUid: AccountUid,
      txView: TransactionView,
      blockView: BlockView,
      sort: Sort
  ): IsEq[F[List[TransactionView]]] = {
    val withBlockTx = txView.copy(block = Some(blockView))
    val updatedTx   = txView.copy(block = None)

    val prgm =
      for {
        _         <- saveTransaction(accountUid, withBlockTx)
        _         <- saveTransaction(accountUid, updatedTx)
        readAgain <- getTransactions(accountUid, txView.hash, sort)
      } yield readAgain

    prgm <-> F.pure(List(updatedTx))
  }

  def deleteRejectedActuallyDeletes(                                     accountUid: AccountUid,
                                     txView: TransactionView, sort: Sort): IsEq[F[Boolean]] = {
    val noBlockTx = txView.copy(block = None)
    val prgm =
      for {
        _ <- saveTransaction(accountUid, noBlockTx)
        _ <- persistenceFacade.operationRepository.deleteRejectedTransaction(accountUid, txView.hash)
        readAgain <- getTransactions(accountUid, txView.hash, sort)
      } yield readAgain.isEmpty

    assert(prgm)
  }

  def outputAmountIsSumOfExternalOutputs(accountUid: AccountUid, txView: TransactionView, sort: Sort): IsEq[F[Satoshis]] = {
    val expected = F.pure(txView.outputs.filter(_.changeType.contains(ChangeType.External)).foldMap(_.value))
    val actual =
      for {
        _ <- saveTransaction(accountUid, txView)
        amounts <- persistenceFacade.operationComputationService.getUncomputedOperations(accountUid, sort, None).compile.toList
        amount::Nil = amounts
      } yield amount.outputAmount

    expected <-> actual
  }

  def changeAmountIsSumOfInternalOutputs(accountUid: AccountUid, txView: TransactionView, sort: Sort): IsEq[F[Satoshis]] = {
    val expected = F.pure(txView.outputs.filter(_.changeType.contains(ChangeType.Internal)).foldMap(_.value))
    val actual =
      for {
        _ <- saveTransaction(accountUid, txView)
        amounts <- persistenceFacade.operationComputationService.getUncomputedOperations(accountUid, sort, None).compile.toList
        amount::Nil = amounts
      } yield amount.changeAmount

    expected <-> actual
  }

  def inputAmountIsSumOfTxInputsWithDerivation(accountUid: AccountUid, txView: TransactionView, sort: Sort): IsEq[F[Satoshis]] = {
    val expected = F.pure(txView.inputs.filter(_.belongs).foldMap(_.value))
    val actual =
      for {
        _ <- saveTransaction(accountUid, txView)
        amounts <- persistenceFacade.operationComputationService.getUncomputedOperations(accountUid, sort, None).compile.toList
        amount::Nil = amounts
      } yield amount.inputAmount

    expected <-> actual
  }

  def operationFeesMatchTxFees(accountUid: AccountUid, txView: TransactionView, sort: Sort): IsEq[F[Satoshis]] = {
    val expected = F.pure(txView.fees)
    val actual =
      for {
        _ <- saveTransaction(accountUid, txView)
        amounts <- persistenceFacade.operationComputationService.getUncomputedOperations(accountUid, sort, None).compile.toList
        amount::Nil = amounts
      } yield amount.fees

    expected <-> actual
  }

  private def saveTransaction(accountUid: AccountUid, transactionView: TransactionView): F[Unit] =
    fs2.Stream
      .emit(AccountTxView(accountUid, transactionView))
      .through(persistenceFacade.transactionRecordRepository.saveTransactions)
      .compile
      .drain

  private def getTransactions(
      accountUid: AccountUid,
      txHash: TxHash,
      sort: Sort
  ): F[List[TransactionView]] =
    persistenceFacade.operationComputationService
      .fetchTransactions(accountUid, sort, NonEmptyList.one(txHash))
      .compile
      .toList

  private def assert(b: F[Boolean]): IsEq[F[Boolean]] =
    IsEq(b, F.pure(true))
}

object PersistenceFacadeLaws {
  def apply[F[_]](
      persistenceFacade_ : => PersistenceFacade[F]
  )(implicit F_ : Monad[F], fs2Compiler_ : fs2.Stream.Compiler[F, F]): PersistenceFacadeLaws[F] =
    new PersistenceFacadeLaws[F] {
      override def F = F_

      override def fs2Compiler: Stream.Compiler[F, F] = fs2Compiler_

      override lazy val persistenceFacade: PersistenceFacade[F] = persistenceFacade_
    }
}
