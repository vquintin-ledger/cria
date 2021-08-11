package co.ledger.cria.domain.services.interpreter

import co.ledger.cria.domain.models.account.AccountUid

trait PostSyncCheckService[F[_]] {

  def check(accountId: AccountUid): F[Unit]
}
