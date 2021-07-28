package co.ledger.cria.clients.explorer.models

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import co.ledger.cria.clients.explorer.models.circeImplicits._

case class TransactionHex(transactionHash: String, hex: String)

object TransactionHex {
  implicit val encoder: Encoder[TransactionHex] = deriveConfiguredEncoder[TransactionHex]
  implicit val decoder: Decoder[TransactionHex] = deriveConfiguredDecoder[TransactionHex]
}
