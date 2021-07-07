package co.ledger.cria.clients.explorer.models

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import circeImplicits._

case class SendTransactionResult(result: String)

object SendTransactionResult {
  implicit val decoder: Decoder[SendTransactionResult] =
    deriveConfiguredDecoder[SendTransactionResult]
}
