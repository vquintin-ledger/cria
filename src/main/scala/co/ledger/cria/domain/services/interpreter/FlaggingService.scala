package co.ledger.cria.domain.services.interpreter

import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.keychain.AccountAddress

trait FlaggingService {

  def flagInputsAndOutputs(
                            accountId: AccountUid,
                            accountAddresses: List[AccountAddress]
                          )(implicit cs: ContextShift[IO]): IO[Unit]
}
