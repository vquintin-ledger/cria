package co.ledger.cria.clients.explorer.models

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

import java.time.Instant
import circeImplicits._

sealed trait Transaction {
  val id: String
  val hash: String
  val receivedAt: Instant
  val lockTime: Long
  val fees: BigInt
  val inputs: Seq[Input]
  val outputs: Seq[Output]
  val confirmations: Int
}

object Transaction {
  implicit val encoder: Encoder[Transaction] =
    Encoder.instance {
      case confirmedTx: ConfirmedTransaction     => confirmedTx.asJson
      case unconfirmedTx: UnconfirmedTransaction => unconfirmedTx.asJson
    }

  implicit val decoder: Decoder[Transaction] = Decoder[ConfirmedTransaction]
    .map[Transaction](identity)
    .or(Decoder[UnconfirmedTransaction].map[Transaction](identity))
}

case class ConfirmedTransaction(
    id: String,
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[Input],
    outputs: Seq[Output],
    block: Block,
    confirmations: Int
) extends Transaction

object ConfirmedTransaction {
  implicit val encoder: Encoder[ConfirmedTransaction] =
    deriveConfiguredEncoder[ConfirmedTransaction]

  implicit val decoder: Decoder[ConfirmedTransaction] =
    deriveConfiguredDecoder[ConfirmedTransaction]
}

case class UnconfirmedTransaction(
    id: String,
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[Input],
    outputs: Seq[Output],
    confirmations: Int
) extends Transaction

object UnconfirmedTransaction {
  implicit val encoder: Encoder[UnconfirmedTransaction] =
    deriveConfiguredEncoder[UnconfirmedTransaction]

  implicit val decoder: Decoder[UnconfirmedTransaction] =
    deriveConfiguredDecoder[UnconfirmedTransaction]
}
