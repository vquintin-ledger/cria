package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.IO
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockView}
import co.ledger.cria.domain.services.interpreter.TransactionRecordRepository
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

class TransactionRecordRepositoryTee(
    primary: TransactionRecordRepository,
    secondary: TransactionRecordRepository,
    combiner: Combiner
) extends TransactionRecordRepository {

  override def saveTransactions(implicit
      lc: CriaLogContext
  ): Pipe[IO, AccountTxView, Int] =
    combiner.combinePipe(primary.saveTransactions, secondary.saveTransactions)

  override def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    combiner.combineAction(
      primary.removeFromCursor(accountId, blockHeight),
      secondary.removeFromCursor(accountId, blockHeight)
    )

  override def getLastBlocks(accountId: AccountUid): fs2.Stream[IO, BlockView] =
    combiner.combineStream(primary.getLastBlocks(accountId), secondary.getLastBlocks(accountId))

}
