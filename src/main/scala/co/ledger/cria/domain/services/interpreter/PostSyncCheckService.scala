package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.CurrentBalance
import doobie._
import doobie.implicits._

class PostSyncCheckService(db: Transactor[IO]) extends ContextLogging {

  def check(accountId: AccountUid): IO[Unit] = {
    implicit val lc: CriaLogContext = CriaLogContext().withAccountId(accountId)

    for {
      currentBalance <- getCurrentBalance(accountId)
      _              <- log.info(s"Balance is ${currentBalance}")
      _              <- checkBalance(currentBalance)
    } yield ()
  }

  private def getCurrentBalance(accountId: AccountUid): IO[CurrentBalance] =
    for {
      blockchainBalance  <- BalanceQueries.getBlockchainBalance(accountId).transact(db)
      unconfirmedBalance <- BalanceQueries.getUnconfirmedBalance(accountId).transact(db)
    } yield CurrentBalance(
      balance = blockchainBalance.balance,
      utxos = blockchainBalance.utxos,
      received = blockchainBalance.received,
      netSent = blockchainBalance.netSent,
      fees = blockchainBalance.fees,
      unconfirmedBalance = unconfirmedBalance
    )

  private def checkBalance(balance: CurrentBalance): IO[Unit] = {
    val balanceFromFlow = balance.received - balance.netSent - balance.fees
    IO.raiseUnless(balanceFromFlow == balance.balance)(
      new RuntimeException(
        s"Balance invariant is not respected. received - sent = ${balanceFromFlow}. balance = ${balance.balance}"
      )
    )
  }
}
