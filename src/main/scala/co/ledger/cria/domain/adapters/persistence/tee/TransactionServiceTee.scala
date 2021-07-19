package co.ledger.cria.domain.adapters.persistence.tee

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockView, TransactionView}
import co.ledger.cria.domain.services.interpreter.TransactionService
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

class TransactionServiceTee(primary: TransactionService, secondary: TransactionService, combiner: Combiner) extends TransactionService {
  override def saveTransactions(implicit cs: ContextShift[IO], lc: CriaLogContext): Pipe[IO, AccountTxView, Int] =
    combiner.combinePipe(primary.saveTransactions, secondary.saveTransactions)

  override def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    combiner.combineAction(primary.removeFromCursor(accountId, blockHeight), secondary.removeFromCursor(accountId, blockHeight))

  override def getLastBlocks(accountId: AccountUid): fs2.Stream[IO, BlockView] =
    combiner.combineStream(primary.getLastBlocks(accountId), secondary.getLastBlocks(accountId))

  override def deleteUnconfirmedTransaction(accountId: AccountUid, hash: TxHash): IO[String] =
    combiner.combineAction(primary.deleteUnconfirmedTransaction(accountId, hash), secondary.deleteUnconfirmedTransaction(accountId, hash))

  override def fetchTransactions(accountId: AccountUid, sort: Sort, hashes: NonEmptyList[TxHash]): fs2.Stream[IO, TransactionView] =
    combiner.combineStream(primary.fetchTransactions(accountId, sort, hashes), secondary.fetchTransactions(accountId,sort, hashes))
}
