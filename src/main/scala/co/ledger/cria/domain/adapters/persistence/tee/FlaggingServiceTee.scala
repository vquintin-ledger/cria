package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.keychain.AccountAddress
import co.ledger.cria.domain.services.interpreter.FlaggingService

final class FlaggingServiceTee(
    primary: FlaggingService,
    secondary: FlaggingService,
    combiner: Combiner
) extends FlaggingService {
  override def flagInputsAndOutputs(accountId: AccountUid, accountAddresses: List[AccountAddress])(
      implicit cs: ContextShift[IO]
  ): IO[Unit] =
    combiner.combineAction(
      primary.flagInputsAndOutputs(accountId, accountAddresses),
      secondary.flagInputsAndOutputs(accountId, accountAddresses)
    )
}
