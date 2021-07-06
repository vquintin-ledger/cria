package co.ledger.cria.domain.models.interpreter

import java.time.Instant
import co.ledger.cria.domain.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class BlockView(
    hash: BlockHash,
    height: Long,
    time: Instant
)

object BlockView {
  implicit val encoder: Encoder[BlockView] = deriveConfiguredEncoder[BlockView]
  implicit val decoder: Decoder[BlockView] = deriveConfiguredDecoder[BlockView]

  implicit val ordering: Ordering[BlockView] = Ordering.by(b => (b.height, b.time))
}
