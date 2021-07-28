package co.ledger.cria.domain.adapters.persistence.lama

import cats.effect.IO
import co.ledger.cria.domain.adapters.persistence.lama.models.OperationToSave
import co.ledger.cria.domain.adapters.persistence.lama.queries.{
  LamaOperationQueries,
  LamaTransactionQueries
}
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.domain.services.interpreter.OperationRepository
import co.ledger.cria.logging.CriaLogContext
import doobie.Transactor
import doobie.implicits._

final class LamaOperationRepository(
    db: Transactor[IO]
) extends OperationRepository {

  override def saveTransaction(
      coin: Coin,
      accountUid: AccountUid,
      transactionView: TransactionView
  ): IO[Unit] = IO.unit

  override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit
      lc: CriaLogContext
  ): IO[Unit] = IO.unit

  override def saveOperation(
      coin: Coin,
      accountUid: AccountUid,
      walletUid: WalletUid,
      op: Operation
  ): IO[Int] = {
    val opToSave = OperationToSave.fromOperation(op)
    LamaOperationQueries
      .saveOperations(List(opToSave))
      .transact(db)
  }

  override def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): IO[Int] =
    LamaTransactionQueries
      .deleteRejectedTransaction(accountId, hash)
      .transact(db)

}
