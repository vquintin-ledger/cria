package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.IO
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.domain.services.interpreter.OperationRepository
import co.ledger.cria.logging.CriaLogContext

final class OperationRepositoryTee(
    primary: OperationRepository,
    secondary: OperationRepository,
    combiner: Combiner
) extends OperationRepository {

  override def saveOperation(
      coin: Coin,
      accountUid: AccountUid,
      walletUid: WalletUid,
      op: Operation
  ): IO[Int] =
    combiner.combineAction(
      primary.saveOperation(coin, accountUid, walletUid, op),
      secondary.saveOperation(coin, accountUid, walletUid, op)
    )

  override def saveTransaction(
      coin: Coin,
      accountUid: AccountUid,
      transactionView: TransactionView
  ): IO[Unit] =
    combiner.combineAction(
      primary.saveTransaction(coin, accountUid, transactionView),
      secondary.saveTransaction(coin, accountUid, transactionView)
    )

  override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit
      lc: CriaLogContext
  ): IO[Unit] =
    combiner.combineAction(
      primary.saveBlocks(coin, blocks),
      secondary.saveBlocks(coin, blocks)
    )

  override def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): IO[String] =
    combiner.combineAction(
      primary.deleteRejectedTransaction(accountId, hash),
      secondary.deleteRejectedTransaction(accountId, hash)
    )
}
