package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.domain.adapters.wd.models.WDOperation
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, TransactionView}
import co.ledger.cria.logging.CriaLogContext

trait WDService {

  def saveWDOperation(op: WDOperation): IO[Int]

  def saveTransaction(coin: Coin, accountUid: AccountUid, transactionView: TransactionView): IO[Int]

  def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit lc: CriaLogContext): IO[Int]

  def removeFromCursor(blockHeight: Option[Long]): IO[Int]
}
