package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.wd.queries.{WDTransactionQueries, WDQueries}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockView}
import co.ledger.cria.domain.services.interpreter.TransactionRecordRepository
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import doobie.Transactor
import doobie.implicits._
import fs2._

final class WDTransactionRecordRepository(db: Transactor[IO], maxConcurrent: Int)(implicit
    cs: ContextShift[IO]
) extends ContextLogging
    with TransactionRecordRepository {

  override def saveTransactions(implicit lc: CriaLogContext): Pipe[IO, AccountTxView, Int] =
    _.chunkN(100)
      .parEvalMapUnordered(maxConcurrent) { chunk =>
        Stream
          .chunk(chunk)
          .evalMap(a => WDTransactionQueries.saveTransaction(a.accountId, a.tx))
          .transact(db)
          .compile
          .foldMonoid
          .flatMap { nbSaved =>
            log.info(s"$nbSaved new transactions saved (from chunk size: ${chunk.size})") *>
              IO.pure(nbSaved)
          }
      }

  override def removeFromCursor(accountUid: AccountUid, blockHeight: Long): IO[Int] = {
    // remove block & operations & transactions & inputs
    WDQueries
      .removeFromCursor(blockHeight)
      .transact(db)
  }

  override def getLastBlocks(accountId: AccountUid): Stream[IO, BlockView] =
    WDTransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

}
