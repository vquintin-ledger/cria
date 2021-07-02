package co.ledger.cria.domain.models.interpreter

import java.time.Instant

import co.ledger.cria.domain.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class TransactionView(
    id: String,
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[InputView],
    outputs: Seq[OutputView],
    block: Option[BlockView],
    confirmations: Int
)

object TransactionView {
  implicit val encoder: Encoder[TransactionView] = deriveConfiguredEncoder[TransactionView]
  implicit val decoder: Decoder[TransactionView] = deriveConfiguredDecoder[TransactionView]
}
