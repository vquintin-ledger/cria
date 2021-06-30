package co.ledger.cria.domain.models.keychain

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.domain.models.circeImplicits._

import java.util.UUID
import scala.util.Try

case class KeychainId(value: UUID) extends AnyVal

object KeychainId {
  implicit val decoder: Decoder[KeychainId] = deriveConfiguredDecoder[KeychainId]
  implicit val encoder: Encoder[KeychainId] = deriveConfiguredEncoder[KeychainId]

  def fromString(value: String): Option[KeychainId] =
    Try(UUID.fromString(value)).toOption.map(KeychainId.apply)
}
