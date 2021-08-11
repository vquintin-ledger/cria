package co.ledger.cria.domain.adapters.persistence.tee

import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.services.interpreter.PostSyncCheckService

final class PostSyncCheckServiceTee[F[_]](
    primary: PostSyncCheckService[F],
    secondary: PostSyncCheckService[F],
    combiner: Combiner[F]
) extends PostSyncCheckService[F] {
  override def check(accountId: AccountUid): F[Unit] =
    combiner.combineAction(primary.check(accountId), secondary.check(accountId))
}
