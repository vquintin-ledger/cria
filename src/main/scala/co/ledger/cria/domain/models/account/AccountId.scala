package co.ledger.cria.domain.models.account

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import co.ledger.cria.domain.models.circeImplicits._

import java.util.UUID
import scala.util.Try

case class AccountId(value: UUID) extends AnyVal

object AccountId {

  implicit val decoder: Decoder[AccountId] = deriveConfiguredDecoder[AccountId]
  implicit val encoder: Encoder[AccountId] = deriveConfiguredEncoder[AccountId]

  def fromString(value: String): Option[AccountId] =
    Try(UUID.fromString(value)).toOption.map(AccountId(_))
}
