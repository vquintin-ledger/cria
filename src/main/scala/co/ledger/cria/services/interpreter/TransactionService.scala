package co.ledger.cria.services.interpreter

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.models.interpreter.{AccountTxView, BlockView}
import doobie.Transactor
import doobie.implicits._
import fs2._

class TransactionService(db: Transactor[IO], maxConcurrent: Int) extends ContextLogging {

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

  def removeFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    TransactionQueries
      .removeFromCursor(accountId, blockHeight)
      .flatMap(_ =>
        TransactionQueries
          .deleteUnconfirmedTransactions(accountId)
      )
      .transact(db)

  def getLastBlocks(accountId: UUID): Stream[IO, BlockView] =
    TransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

  def deleteUnconfirmedTransaction(accountId: UUID, hash: String): IO[String] =
    TransactionQueries
      .deleteUnconfirmedTransaction(accountId, hash)
      .transact(db)

}
