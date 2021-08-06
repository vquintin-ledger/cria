package co.ledger.cria.clients.explorer.v3.models

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import circeImplicits._

import java.time.Instant

case class Block(
    hash: String,
    height: Long,
    time: Instant
)

object Block {
  implicit val encoder: Encoder[Block] = deriveConfiguredEncoder[Block]
  implicit val decoder: Decoder[Block] = deriveConfiguredDecoder[Block].map(b =>
    if (b.hash.startsWith("0x")) b.copy(hash = b.hash.substring(2)) else b
  )

  implicit val ordering: Ordering[Block] = Ordering.by(b => (b.height, b.time))
}
