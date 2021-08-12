package co.ledger.cria.domain.adapters.persistence.wd

import cats.data.NonEmptyList
import cats.implicits._
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.wd.queries.{WDQueries, WDTransactionQueries}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockHeight, BlockView}
import co.ledger.cria.domain.services.interpreter.TransactionRecordRepository
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import doobie.Transactor
import doobie.free.connection
import doobie.implicits._
import fs2._
import shapeless.tag.@@

final class WDTransactionRecordRepository(
    walletDaemon: Transactor[IO] @@ DBType.WD,
    criaExtra: Transactor[IO] @@ DBType.Temporary,
    maxConcurrent: Int
)(implicit
    cs: ContextShift[IO]
) extends ContextLogging
    with TransactionRecordRepository {

  override def saveTransactions(implicit lc: CriaLogContext): Pipe[IO, AccountTxView, Unit] =
    _.chunkN(100)
      .parEvalMapUnordered(maxConcurrent) { chunk =>
        Stream
          .chunk(chunk)
          .evalMap(a => WDTransactionQueries.saveTransaction(a.accountId, a.tx))
          .transact(criaExtra)
          .compile
          .foldMonoid
          .flatMap { nbSaved =>
            log.info(s"$nbSaved new transactions saved (from chunk size: ${chunk.size})")
          }
      }

  override def removeFromCursor(accountUid: AccountUid, blockHeight: BlockHeight): IO[Int] = {
    // remove block & operations & transactions & inputs
    // search inputs attached to blocks to remove
    (for {
      inUids <- WDQueries.getInputUidsFromBlockHeight(blockHeight.value)
      _ <- inUids match {
        case Nil          => connection.pure[Int](0)
        case head :: tail => WDQueries.deleteInputs(NonEmptyList(head, tail))
      }
      blocks <- WDQueries.deleteBlock(blockHeight.value)
    } yield blocks).transact(walletDaemon)
  }

  override def getLastBlocks(accountId: AccountUid): Stream[IO, BlockView] =
    WDTransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(walletDaemon)

}
