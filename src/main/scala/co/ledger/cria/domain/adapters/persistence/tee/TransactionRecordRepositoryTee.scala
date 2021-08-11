package co.ledger.cria.domain.adapters.persistence.tee

import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockHash, BlockHeight, BlockView}
import co.ledger.cria.domain.services.interpreter.TransactionRecordRepository
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

class TransactionRecordRepositoryTee[F[_]](
    primary: TransactionRecordRepository[F],
    secondary: TransactionRecordRepository[F],
    combiner: Combiner[F]
) extends TransactionRecordRepository[F] {

  override def saveTransactions(implicit
      lc: CriaLogContext
  ): Pipe[F, AccountTxView, Unit] =
    combiner.combinePipe(primary.saveTransactions, secondary.saveTransactions)

  override def removeFromCursor(accountId: AccountUid, blockHeight: BlockHeight): F[Int] =
    combiner.combineAction(
      primary.removeFromCursor(accountId, blockHeight),
      secondary.removeFromCursor(accountId, blockHeight)
    )

  override def getLastBlocks(accountId: AccountUid): fs2.Stream[F, BlockView] =
    combiner.combineStream(primary.getLastBlocks(accountId), secondary.getLastBlocks(accountId))

  override def getLastBlockHash(accountId: AccountUid): F[Option[BlockHash]] =
    combiner.combineAction(
      primary.getLastBlockHash(accountId),
      secondary.getLastBlockHash(accountId)
    )

}
