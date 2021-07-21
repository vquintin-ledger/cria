package co.ledger.cria.domain.adapters.persistence.lama

import cats.effect.IO
import co.ledger.cria.domain.adapters.persistence.lama.models.OperationToSave
import co.ledger.cria.domain.adapters.persistence.lama.queries.
  OperationQueries
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.domain.services.interpreter.{TransactionService, WDService}
import co.ledger.cria.logging.CriaLogContext
import doobie.Transactor
import doobie.implicits._

final class WDServiceImpl(db: Transactor[IO], transactionService: TransactionService)
    extends WDService {
  override def saveWDOperation(
      coin: Coin,
      accountUid: AccountUid,
      walletUid: WalletUid,
      op: Operation
  ): IO[Int] = {
    val opToSave = OperationToSave.fromOperation(op)
    OperationQueries
      .saveOperations(List(opToSave))
      .transact(db)
  }

  override def saveTransaction(
      coin: Coin,
      accountUid: AccountUid,
      transactionView: TransactionView
  ): IO[Unit] = IO.unit

  override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit
      lc: CriaLogContext
  ): IO[Int] =
    IO.pure(blocks.size)

  override def removeFromCursor(accountUid: AccountUid, blockHeight: Option[Long]): IO[Int] =
    blockHeight.fold(IO.pure(0))(h => transactionService.removeFromCursor(accountUid, h))
}
