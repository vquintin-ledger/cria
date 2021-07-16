package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.domain.models.account.AccountUid

trait PostSyncCheckService {

  def check(accountId: AccountUid): IO[Unit]
}
