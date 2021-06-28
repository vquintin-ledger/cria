package co.ledger.cria.services.interpreter

import co.ledger.cria.models.interpreter.Operation
import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

final case class OperationPaginationState(uid: Operation.UID, blockHeight: Long)

object OperationPaginationState {
  implicit val encoder: Encoder[OperationPaginationState] =
    deriveConfiguredEncoder[OperationPaginationState]

  implicit val decoder: Decoder[OperationPaginationState] =
    deriveConfiguredDecoder[OperationPaginationState]
}
