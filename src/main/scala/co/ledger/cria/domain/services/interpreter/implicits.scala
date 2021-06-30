package co.ledger.cria.domain.services.interpreter

import co.ledger.cria.domain.models.account.AccountId
import doobie.Meta

import doobie.postgres.implicits._

import java.util.UUID

object implicits {
  implicit val doobieMetaAccountId: Meta[AccountId] =
    Meta[UUID].timap[AccountId](AccountId(_))(_.value)
}
