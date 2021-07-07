package co.ledger.cria.domain.models.keychain

import java.util.UUID
import scala.util.Try

case class KeychainId(value: UUID) extends AnyVal

object KeychainId {
  def fromString(value: String): Option[KeychainId] =
    Try(UUID.fromString(value)).toOption.map(KeychainId.apply)
}
