package co.ledger.cria.domain.adapters.persistence.lama

import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.lama.queries.LamaTransactionQueries
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockHash, BlockHeight, BlockView}
import co.ledger.cria.domain.services.interpreter.TransactionRecordRepository
import doobie.Transactor
import doobie.implicits._
import fs2._
import cats.implicits._

final class LamaTransactionRecordRepository(db: Transactor[IO], maxConcurrent: Int)(implicit
    cs: ContextShift[IO]
) extends TransactionRecordRepository
    with ContextLogging {

  override def saveTransactions(implicit
      lc: CriaLogContext
  ): Pipe[IO, AccountTxView, Unit] =
    _.chunkN(100)
      .parEvalMapUnordered(maxConcurrent) { chunk =>
        Stream
          .chunk(chunk)
          .evalMap(a => LamaTransactionQueries.saveTransaction(a.accountId, a.tx))
          .transact(db)
          .compile
          .foldMonoid
          .flatMap { nbSaved =>
            log.info(s"$nbSaved new transactions saved (from chunk size: ${chunk.size})")
          }
      }

  override def removeFromCursor(accountId: AccountUid, blockHeight: BlockHeight): IO[Int] =
    LamaTransactionQueries
      .removeFromCursor(accountId, blockHeight.value)
      .transact(db)

  override def getLastBlocks(accountId: AccountUid): Stream[IO, BlockView] =
    LamaTransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

  override def getLastBlockHash(accountId: AccountUid): IO[Option[BlockHash]] =
    LamaTransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)
      .compile
      .toList
      .map(_.headOption.map(_.hash))

}
