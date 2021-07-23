package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.IO
import co.ledger.cria.domain.adapters.persistence.wd.models.{WDBlock, WDOperation, WDTransaction}
import co.ledger.cria.domain.adapters.persistence.wd.queries.{WDTransactionQueries, WDQueries}
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.domain.services.interpreter.OperationRepository
import co.ledger.cria.logging.{CriaLogContext, DefaultContextLogging}
import doobie.Transactor
import doobie.implicits._

class WDOperationRepository(
    db: Transactor[IO]
) extends DefaultContextLogging
    with OperationRepository {

  override def saveOperation(
      coin: Coin,
      accountUid: AccountUid,
      walletUid: WalletUid,
      op: Operation
  ): IO[Int] = {
    val wdOp = WDOperation.fromOperation(accountUid, op, coin, walletUid)
    WDQueries
      .saveWDOperation(wdOp)
      .transact(db)
  }

  override def saveTransaction(
      coin: Coin,
      accountUid: AccountUid,
      transactionView: TransactionView
  ): IO[Unit] = {
    val tx = WDTransaction.fromTransactionView(accountUid, transactionView, coin)
    for {
      txStatement <- WDQueries.saveTransaction(tx)
      _           <- WDQueries.saveInputs(tx, tx.inputs)
      _           <- WDQueries.saveOutputs(tx.outputs, tx.uid, tx.hash)
    } yield txStatement
  }.transact(db).void

  override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit
      lc: CriaLogContext
  ): IO[Unit] = {
    val wdBlocks = blocks.map(WDBlock.fromBlock(_, coin))
    log.info(s"Saving ${blocks.size} WD Blocks") *>
      WDQueries
        .saveBlocks(wdBlocks)
        .transact(db)
        .void
  }

  //TODO: not doing what it should, FIX plz
  override def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): IO[String] =
    WDTransactionQueries
      .deleteRejectedTransaction(accountId, hash)
      .transact(db)
}
