package co.ledger.cria.clients.explorer.v2.models

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import circeImplicits._

import java.time.Instant

case class Transaction(
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[Input],
    outputs: Seq[Output],
    block: Option[Block],
    confirmations: Int
)

object Transaction {
  implicit val encoder: Encoder[Transaction] = deriveConfiguredEncoder[Transaction]

  implicit val decoder: Decoder[Transaction] = deriveConfiguredDecoder[Transaction]
}
