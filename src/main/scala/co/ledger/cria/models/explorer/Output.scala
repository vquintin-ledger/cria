package co.ledger.cria.models.explorer

import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

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
