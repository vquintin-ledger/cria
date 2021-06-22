package co.ledger.cria.models.interpreter

import cats.data.NonEmptyList
import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class InputView(
    outputHash: String,
    outputIndex: Int,
    inputIndex: Int,
    value: BigInt,
    address: String,
    scriptSignature: String,
    txinwitness: List[String],
    sequence: Long,
    derivation: Option[NonEmptyList[Int]]
) {
  val belongs: Boolean = derivation.isDefined
}

object InputView {
  implicit val encoder: Encoder[InputView] = deriveConfiguredEncoder[InputView]
  implicit val decoder: Decoder[InputView] = deriveConfiguredDecoder[InputView]
}
