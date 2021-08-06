package co.ledger.cria.itutils.models

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.clients.explorer.v3.models.circeImplicits._

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
