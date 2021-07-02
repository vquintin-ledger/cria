package co.ledger.cria.clients.explorer.models

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.domain.models.circeImplicits._

case class GetTransactionsResponse(truncated: Boolean, txs: Seq[Transaction])

object GetTransactionsResponse {
  implicit val encoder: Encoder[GetTransactionsResponse] =
    deriveConfiguredEncoder[GetTransactionsResponse]
  implicit val decoder: Decoder[GetTransactionsResponse] =
    deriveConfiguredDecoder[GetTransactionsResponse]
}
