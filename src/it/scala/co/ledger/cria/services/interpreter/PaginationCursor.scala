package co.ledger.cria.services.interpreter

import co.ledger.cria.models.circeImplicits._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}

case class PaginationCursor(
    previous: Option[String],
    next: Option[String]
)

object PaginationCursor {
  implicit val encoder: Encoder[PaginationCursor] =
    deriveConfiguredEncoder[PaginationCursor]

  implicit val decoder: Decoder[PaginationCursor] =
    deriveConfiguredDecoder[PaginationCursor]
}
