package co.ledger.cria.domain.adapters.persistence.wd

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.wd.queries.TransactionQueries
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockView, TransactionView}
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.services.interpreter.TransactionService
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import doobie.Transactor
import doobie.implicits._
import fs2._

class TransactionServiceImpl(db: Transactor[IO], maxConcurrent: Int)
    extends ContextLogging
    with TransactionService {

  override def saveTransactions(implicit
      cs: ContextShift[IO],
      lc: CriaLogContext
  ): Pipe[IO, AccountTxView, Int] =
    _.chunkN(100)
      .parEvalMapUnordered(maxConcurrent) { chunk =>
        Stream
          .chunk(chunk)
          .evalMap(a => TransactionQueries.saveTransaction(a.accountId, a.tx))
          .transact(db)
          .compile
          .foldMonoid
          .flatMap { nbSaved =>
            log.info(s"$nbSaved new transactions saved (from chunk size: ${chunk.size})") *>
              IO.pure(nbSaved)
          }
      }

  override def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    TransactionQueries
      .removeFromCursor(accountId, blockHeight)
      .flatMap(_ =>
        TransactionQueries
          .deleteUnconfirmedTransactions(accountId)
      )
      .transact(db)

  override def getLastBlocks(accountId: AccountUid): Stream[IO, BlockView] =
    TransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

  override def deleteUnconfirmedTransaction(accountId: AccountUid, hash: TxHash): IO[String] =
    TransactionQueries
      .deleteUnconfirmedTransaction(accountId, hash)
      .transact(db)

  override def fetchTransactions(
      accountId: AccountUid,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): Stream[IO, TransactionView] = {
    val txStream = TransactionQueries.fetchTransaction(accountId, sort, hashes).transact(db)
    val txDetailsStream =
      TransactionQueries.fetchTransactionDetails(accountId, sort, hashes).transact(db)

    txStream
      .zip(txDetailsStream)
      .collect {
        case (tx, details) if tx.hash == details.txHash =>
          tx.copy(inputs = details.inputs, outputs = details.outputs)
      }

  }

}
