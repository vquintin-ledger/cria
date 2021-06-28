package co.ledger.cria.itutils.models

import co.ledger.cria.models.interpreter.Operation
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.models.circeImplicits._

case class GetOperationsResult(
    operations: List[Operation],
    total: Int,
    cursor: Option[PaginationCursor]
)

object GetOperationsResult {
  implicit val decoder: Decoder[GetOperationsResult] = deriveConfiguredDecoder[GetOperationsResult]
  implicit val encoder: Encoder[GetOperationsResult] = deriveConfiguredEncoder[GetOperationsResult]
}
