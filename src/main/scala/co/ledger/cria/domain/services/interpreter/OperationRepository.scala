package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.logging.CriaLogContext

trait OperationRepository {

  def saveOperation(
      coin: Coin,
      accountUid: AccountUid,
      walletUid: WalletUid,
      op: Operation
  ): IO[Int]

  def saveTransaction(
      coin: Coin,
      accountUid: AccountUid,
      transactionView: TransactionView
  ): IO[Unit]

  def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit lc: CriaLogContext): IO[Unit]

  def deleteRejectedTransaction(accountId: AccountUid, hash: TxHash): IO[String]

}
