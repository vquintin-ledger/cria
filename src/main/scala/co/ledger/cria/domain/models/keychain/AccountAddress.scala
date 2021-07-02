package co.ledger.cria.domain.models.keychain

import cats.data.NonEmptyList
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.domain.models.circeImplicits._

case class AccountAddress(
    accountAddress: String,
    changeType: ChangeType,
    derivation: NonEmptyList[Int]
)

object AccountAddress {
  implicit val encoder: Encoder[AccountAddress] = deriveConfiguredEncoder[AccountAddress]
  implicit val decoder: Decoder[AccountAddress] = deriveConfiguredDecoder[AccountAddress]
}
