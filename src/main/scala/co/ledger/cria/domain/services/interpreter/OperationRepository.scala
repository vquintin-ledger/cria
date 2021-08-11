package co.ledger.cria.domain.services.interpreter

import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.logging.CriaLogContext

trait OperationRepository[F[_]] {

  def saveOperation(
      coin: Coin,
      accountUid: AccountUid,
      walletUid: WalletUid,
      op: Operation
  ): F[Int]

  def saveTransaction(
      coin: Coin,
      accountUid: AccountUid,
      transactionView: TransactionView
  ): F[Unit]

  def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit lc: CriaLogContext): F[Unit]

  def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): F[Int]

}
