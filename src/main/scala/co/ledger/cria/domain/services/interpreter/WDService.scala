package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.domain.adapters.wd.models.{WDOperation, WDTransaction}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin}
import co.ledger.cria.logging.CriaLogContext

trait WDService {

  def saveWDOperation(op: WDOperation): IO[Int]

  def saveTransaction(tx: WDTransaction): IO[Int]

  def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit lc: CriaLogContext): IO[Int]

  def removeFromCursor(blockHeight: Option[Long]): IO[Int]
}
