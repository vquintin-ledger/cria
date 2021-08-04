package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockView}
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

trait TransactionRecordRepository {

  def saveTransactions(implicit lc: CriaLogContext): Pipe[IO, AccountTxView, Unit]

  def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int]

  def getLastBlocks(accountId: AccountUid): fs2.Stream[IO, BlockView]

}
