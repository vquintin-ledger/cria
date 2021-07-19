package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.IO
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Operation, TransactionView}
import co.ledger.cria.domain.services.interpreter.WDService
import co.ledger.cria.logging.CriaLogContext

final class WDServiceTee(primary: WDService, secondary: WDService, combiner: Combiner) extends WDService {
  override def saveWDOperation(coin: Coin, accountUid: AccountUid, walletUid: WalletUid, op: Operation): IO[Int] =
    combiner.combineAction(primary.saveWDOperation(coin, accountUid, walletUid, op), secondary.saveWDOperation(coin, accountUid, walletUid, op))

  override def saveTransaction(coin: Coin, accountUid: AccountUid, transactionView: TransactionView): IO[Unit] =
    combiner.combineAction(primary.saveTransaction(coin, accountUid, transactionView), secondary.saveTransaction(coin, accountUid, transactionView))

  override def saveBlocks(coin: Coin, blocks: List[BlockView])(implicit lc: CriaLogContext): IO[Int] =
    combiner.combineAction(primary.saveBlocks(coin, blocks), secondary.saveBlocks(coin, blocks))

  override def removeFromCursor(accountUid: AccountUid, blockHeight: Option[Long]): IO[Int] =
    combiner.combineAction(primary.removeFromCursor(accountUid, blockHeight), secondary.removeFromCursor(accountUid, blockHeight))
}
