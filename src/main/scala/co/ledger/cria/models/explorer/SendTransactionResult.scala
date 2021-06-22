package co.ledger.cria.models.explorer

import co.ledger.cria.models.circeImplicits._
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder

case class SendTransactionResult(result: String)

object SendTransactionResult {
  implicit val decoder: Decoder[SendTransactionResult] =
    deriveConfiguredDecoder[SendTransactionResult]
}
