package co.ledger.cria.domain.adapters.persistence.lama

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.lama.queries.TransactionQueries
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockView, TransactionView}
import co.ledger.cria.domain.services.interpreter.TransactionService
import doobie.Transactor
import doobie.implicits._
import fs2._

final class TransactionServiceImpl(db: Transactor[IO], maxConcurrent: Int) extends TransactionService with ContextLogging {

  def saveTransactions(implicit
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

  def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    TransactionQueries
      .removeFromCursor(accountId, blockHeight)
      .flatMap(_ =>
        TransactionQueries
          .deleteUnconfirmedTransactions(accountId)
      )
      .transact(db)

  def getLastBlocks(accountId: AccountUid): Stream[IO, BlockView] =
    TransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

  def deleteUnconfirmedTransaction(accountId: AccountUid, hash: TxHash): IO[String] =
    TransactionQueries
      .deleteUnconfirmedTransaction(accountId, hash)
      .transact(db)

  override def fetchTransactions(accountId: AccountUid, sort: Sort, hashes: NonEmptyList[TxHash]): Stream[IO, TransactionView] =
    ???
}
