package co.ledger.cria.services.interpreter

import co.ledger.cria.models.interpreter.Operation
import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class GetOperationsResult(
    operations: List[Operation],
    total: Int,
    cursor: Option[PaginationCursor]
)

object GetOperationsResult {

  implicit val decoder: Decoder[GetOperationsResult] = deriveConfiguredDecoder[GetOperationsResult]
  implicit val encoder: Encoder[GetOperationsResult] = deriveConfiguredEncoder[GetOperationsResult]
}
