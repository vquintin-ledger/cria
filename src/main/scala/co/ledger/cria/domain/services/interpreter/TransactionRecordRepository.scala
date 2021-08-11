package co.ledger.cria.domain.services.interpreter

import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockHash, BlockHeight, BlockView}
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

trait TransactionRecordRepository[F[_]] {

  def saveTransactions(implicit lc: CriaLogContext): Pipe[F, AccountTxView, Unit]

  def removeFromCursor(accountId: AccountUid, blockHeight: BlockHeight): F[Int]

  def getLastBlocks(accountId: AccountUid): fs2.Stream[F, BlockView]

  def getLastBlockHash(accountId: AccountUid): F[Option[BlockHash]]

}
