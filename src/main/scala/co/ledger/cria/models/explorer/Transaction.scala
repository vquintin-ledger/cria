package co.ledger.cria.models.explorer

import java.time.Instant

import co.ledger.cria.models.interpreter.{BlockView, InputView, OutputView, TransactionView}
import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

sealed trait Transaction {
  val id: String
  val hash: String
  val receivedAt: Instant
  val lockTime: Long
  val fees: BigInt
  val inputs: Seq[Input]
  val outputs: Seq[Output]
  val confirmations: Int

  def toTransactionView: TransactionView
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
) extends Transaction {
  def toTransactionView: TransactionView =
    TransactionView(
      id,
      hash,
      receivedAt,
      lockTime,
      fees,
      inputs.collect { case i: DefaultInput =>
        InputView(
          i.outputHash,
          i.outputIndex,
          i.inputIndex,
          i.value,
          i.address,
          i.scriptSignature,
          i.txinwitness,
          i.sequence,
          None
        )
      },
      outputs.map { o =>
        OutputView(
          o.outputIndex,
          o.value,
          o.address,
          o.scriptHex,
          None,
          None
        )
      },
      Some(
        BlockView(
          block.hash,
          block.height,
          block.time
        )
      ),
      confirmations
    )
}

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
) extends Transaction {

  def toTransactionView: TransactionView =
    TransactionView(
      id,
      hash,
      receivedAt,
      lockTime,
      fees,
      inputs.collect { case i: DefaultInput =>
        InputView(
          i.outputHash,
          i.outputIndex,
          i.inputIndex,
          i.value,
          i.address,
          i.scriptSignature,
          i.txinwitness,
          i.sequence,
          None
        )
      },
      outputs.map { o =>
        OutputView(
          o.outputIndex,
          o.value,
          o.address,
          o.scriptHex,
          None,
          None
        )
      },
      None,
      confirmations
    )
}

object UnconfirmedTransaction {
  implicit val encoder: Encoder[UnconfirmedTransaction] =
    deriveConfiguredEncoder[UnconfirmedTransaction]

  implicit val decoder: Decoder[UnconfirmedTransaction] =
    deriveConfiguredDecoder[UnconfirmedTransaction]
}
