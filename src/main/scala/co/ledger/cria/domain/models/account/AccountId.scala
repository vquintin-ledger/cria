package co.ledger.cria.domain.models.account

import java.util.UUID
import scala.util.Try

case class AccountId(value: UUID) extends AnyVal

object AccountId {
  def fromString(value: String): Option[AccountId] =
    Try(UUID.fromString(value)).toOption.map(AccountId(_))
}
