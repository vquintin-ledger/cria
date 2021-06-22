package co.ledger.cria.models.interpreter

import java.time.Instant

import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class BlockView(
    hash: String,
    height: Long,
    time: Instant
)

object BlockView {
  implicit val encoder: Encoder[BlockView] = deriveConfiguredEncoder[BlockView]
  implicit val decoder: Decoder[BlockView] = deriveConfiguredDecoder[BlockView]
}
