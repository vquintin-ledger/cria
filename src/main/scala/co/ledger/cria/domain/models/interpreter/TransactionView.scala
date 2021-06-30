package co.ledger.cria.domain.models.interpreter

import java.time.Instant

import co.ledger.cria.domain.models.circeImplicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

sealed abstract class TransactionView extends Product with Serializable {
  val id: String
  val hash: String
  val receivedAt: Instant
  val lockTime: Long
  val fees: BigInt
  val inputs: Seq[InputView]
  val outputs: Seq[OutputView]
  val blockOpt: Option[BlockView]
  val confirmations: Int
}

case class ConfirmedTransactionView(
    id: String,
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[InputView],
    outputs: Seq[OutputView],
    block: BlockView,
    confirmations: Int
) extends TransactionView {
  override val blockOpt: Option[BlockView] = Some(block)
}

case class UnconfirmedTransactionView(
    id: String,
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[InputView],
    outputs: Seq[OutputView],
    confirmations: Int
) extends TransactionView {
  override val blockOpt: Option[BlockView] = None

  def mine(block: BlockView): ConfirmedTransactionView =
    ConfirmedTransactionView(
      id,
      hash,
      receivedAt,
      lockTime,
      fees,
      inputs,
      outputs,
      block,
      confirmations
    )
}

object TransactionView {

  def apply(
      id: String,
      hash: String,
      receivedAt: Instant,
      lockTime: Long,
      fees: BigInt,
      inputs: Seq[InputView],
      outputs: Seq[OutputView],
      block: Option[BlockView],
      confirmations: Int
  ): TransactionView =
    block.fold[TransactionView](
      UnconfirmedTransactionView(
        id,
        hash,
        receivedAt,
        lockTime,
        fees,
        inputs,
        outputs,
        confirmations
      )
    )(b =>
      ConfirmedTransactionView(
        id,
        hash,
        receivedAt,
        lockTime,
        fees,
        inputs,
        outputs,
        b,
        confirmations
      )
    )

  implicit val encoder: Encoder[TransactionView] = deriveConfiguredEncoder[TransactionView]
  implicit val decoder: Decoder[TransactionView] = deriveConfiguredDecoder[TransactionView]
}
