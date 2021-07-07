package co.ledger.cria.itutils.models

import co.ledger.cria.domain.models.interpreter.Operation
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.clients.explorer.models.circeImplicits._

final case class OperationPaginationState(uid: Operation.UID, blockHeight: Long)

object OperationPaginationState {
  implicit val encoder: Encoder[OperationPaginationState] =
    deriveConfiguredEncoder[OperationPaginationState]

  implicit val decoder: Decoder[OperationPaginationState] =
    deriveConfiguredDecoder[OperationPaginationState]
}
