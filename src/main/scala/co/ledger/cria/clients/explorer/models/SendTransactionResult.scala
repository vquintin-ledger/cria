package co.ledger.cria.clients.explorer.models

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import co.ledger.cria.domain.models.circeImplicits._

case class SendTransactionResult(result: String)

object SendTransactionResult {
  implicit val decoder: Decoder[SendTransactionResult] =
    deriveConfiguredDecoder[SendTransactionResult]
}
