package co.ledger.cria.domain.adapters.wd

import cats.effect.IO
import co.ledger.cria.domain.adapters.wd.models.{WDBlock, WDOperation, WDTransaction}
import co.ledger.cria.domain.adapters.wd.queries.WDQueries
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin}
import co.ledger.cria.domain.services.interpreter.WDService
import co.ledger.cria.logging.{CriaLogContext, DefaultContextLogging}
import doobie.Transactor
import doobie.implicits._

class WDServiceImpl(
                     db: Transactor[IO]
                   ) extends DefaultContextLogging with WDService {

  override def saveWDOperation(op: WDOperation): IO[Int] =
    WDQueries
      .saveWDOperation(op)
      .transact(db)

  override def saveTransaction(tx: WDTransaction): IO[Int] = {
    for {
      txStatement <- WDQueries.saveTransaction(tx)
      _           <- WDQueries.saveInputs(tx, tx.inputs)
      _           <- WDQueries.saveOutputs(tx.outputs, tx.uid, tx.hash)
    } yield txStatement
  }
    .transact(db)

  override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit lc: CriaLogContext): IO[Int] = {
    val wdBlocks = blocks.map(WDBlock.fromBlock(_, coin))
    log.info(s"Saving ${blocks.size} WD Blocks") *>
      WDQueries
        .saveBlocks(wdBlocks)
        .transact(db)
  }

  override def removeFromCursor(blockHeight: Option[Long]): IO[Int] = {
    // remove block & operations & transactions
    WDQueries
      .deleteBlocksFrom(blockHeight)
      .transact(db)
  }

}
