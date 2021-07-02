package co.ledger.cria.domain.models.interpreter

import cats.data.NonEmptyList
import co.ledger.cria.domain.models.circeImplicits._
import co.ledger.cria.domain.models.keychain.ChangeType
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class OutputView(
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String,
    changeType: Option[ChangeType],
    derivation: Option[NonEmptyList[Int]]
) {
  val belongs: Boolean = derivation.isDefined
}

object OutputView {
  implicit val encoder: Encoder[OutputView] = deriveConfiguredEncoder[OutputView]
  implicit val decoder: Decoder[OutputView] = deriveConfiguredDecoder[OutputView]
}
