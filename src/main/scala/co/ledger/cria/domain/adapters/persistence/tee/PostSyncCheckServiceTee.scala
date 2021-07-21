package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.IO
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.services.interpreter.PostSyncCheckService

final class PostSyncCheckServiceTee(
    primary: PostSyncCheckService,
    secondary: PostSyncCheckService,
    combiner: Combiner
) extends PostSyncCheckService {
  override def check(accountId: AccountUid): IO[Unit] =
    combiner.combineAction(primary.check(accountId), secondary.check(accountId))
}
