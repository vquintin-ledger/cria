package co.ledger.cria.models.explorer

import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._

sealed trait Input

case class DefaultInput(
    outputHash: String,
    outputIndex: Int,
    inputIndex: Int,
    value: BigInt,
    address: String,
    scriptSignature: String,
    txinwitness: List[String],
    sequence: Long
) extends Input

object DefaultInput {
  implicit val encoder: Encoder[DefaultInput] = deriveConfiguredEncoder[DefaultInput]
  implicit val decoder: Decoder[DefaultInput] = deriveConfiguredDecoder[DefaultInput]
}

case class CoinbaseInput(
    coinbase: String,
    inputIndex: Int,
    sequence: Long
) extends Input

object CoinbaseInput {
  implicit val encoder: Encoder[CoinbaseInput] = deriveConfiguredEncoder[CoinbaseInput]
  implicit val decoder: Decoder[CoinbaseInput] = deriveConfiguredDecoder[CoinbaseInput]
}

object Input {
  implicit val encoder: Encoder[Input] =
    Encoder.instance {
      case defaultInput: DefaultInput   => defaultInput.asJson
      case coinbaseInput: CoinbaseInput => coinbaseInput.asJson
    }

  implicit val decoder: Decoder[Input] =
    Decoder[DefaultInput]
      .map[Input](identity)
      .or(Decoder[CoinbaseInput].map[Input](identity))
}
