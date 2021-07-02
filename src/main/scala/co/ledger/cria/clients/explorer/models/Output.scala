package co.ledger.cria.clients.explorer.models

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.domain.models.circeImplicits._

case class Output(
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String
)

object Output {
  implicit val encoder: Encoder[Output] = deriveConfiguredEncoder[Output]
  implicit val decoder: Decoder[Output] = deriveConfiguredDecoder[Output]
}
