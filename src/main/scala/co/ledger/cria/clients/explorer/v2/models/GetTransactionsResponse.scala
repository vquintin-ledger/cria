package co.ledger.cria.clients.explorer.v2.models

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import circeImplicits._

case class GetTransactionsResponse(truncated: Boolean, txs: Seq[Transaction])

object GetTransactionsResponse {
  implicit val encoder: Encoder[GetTransactionsResponse] =
    deriveConfiguredEncoder[GetTransactionsResponse]
  implicit val decoder: Decoder[GetTransactionsResponse] =
    deriveConfiguredDecoder[GetTransactionsResponse]
}
