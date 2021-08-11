package co.ledger.cria.domain.adapters.persistence.tee

import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.domain.services.interpreter.OperationRepository
import co.ledger.cria.logging.CriaLogContext

final class OperationRepositoryTee[F[_]](
    primary: OperationRepository[F],
    secondary: OperationRepository[F],
    combiner: Combiner[F]
) extends OperationRepository[F] {

  override def saveOperation(
      coin: Coin,
      accountUid: AccountUid,
      walletUid: WalletUid,
      op: Operation
  ): F[Int] =
    combiner.combineAction(
      primary.saveOperation(coin, accountUid, walletUid, op),
      secondary.saveOperation(coin, accountUid, walletUid, op)
    )

  override def saveTransaction(
      coin: Coin,
      accountUid: AccountUid,
      transactionView: TransactionView
  ): F[Unit] =
    combiner.combineAction(
      primary.saveTransaction(coin, accountUid, transactionView),
      secondary.saveTransaction(coin, accountUid, transactionView)
    )

  override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit
      lc: CriaLogContext
  ): F[Unit] =
    combiner.combineAction(
      primary.saveBlocks(coin, blocks),
      secondary.saveBlocks(coin, blocks)
    )

  override def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): F[Int] =
    combiner.combineAction(
      primary.deleteRejectedTransaction(accountId, hash),
      secondary.deleteRejectedTransaction(accountId, hash)
    )
}
