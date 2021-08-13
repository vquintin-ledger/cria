package co.ledger.cria.domain.models.interpreter

import cats.MonadError
import cats.data.{Validated, ValidatedNel}
import co.ledger.cria.domain.models.TxHash
import cats.implicits._

import java.time.Instant

final case class TransactionView private (
    id: String,
    hash: TxHash,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[InputView],
    outputs: Seq[OutputView],
    block: Option[BlockView],
    confirmations: Int
)

object TransactionView {

  def apply(
      id: String,
      hash: TxHash,
      receivedAt: Instant,
      lockTime: Long,
      fees: BigInt,
      inputs: Seq[InputView],
      outputs: Seq[OutputView],
      block: Option[BlockView],
      confirmations: Int
  ): ValidatedNel[String, TransactionView] = {
    val sortedInputs  = inputs.sortBy(_.inputIndex)
    val sortedOutputs = outputs.sortBy(_.outputIndex)

    val checkInputs: ValidatedNel[String, Unit] =
      sortedInputs.zipWithIndex.traverse { case (input, idx) =>
        Validated.condNel(
          input.inputIndex == idx,
          (),
          s"input at index $idx has input index ${input.inputIndex}"
        )
      }.void

    val checkOutputs: ValidatedNel[String, Unit] =
      sortedOutputs.zipWithIndex.traverse { case (output, idx) =>
        Validated.condNel(
          output.outputIndex == idx,
          (),
          s"output at index $idx has output index ${output.outputIndex}"
        )
      }.void

    val checkFees: ValidatedNel[String, BigInt] = {
      val computedFees = inputs.foldMap(_.value) - outputs.foldMap(_.value)
      Validated.condNel(fees >= 0, (), s"Fees should be non negative. Got $fees") *>
        Validated.condNel(fees == computedFees, fees, s"Fees does not match inputs/outputs")
    }

    def mkTxView(fees: BigInt): TransactionView =
      new TransactionView(
        id = id,
        hash = hash,
        receivedAt = receivedAt,
        lockTime = lockTime,
        fees = fees,
        inputs = sortedInputs,
        outputs = sortedOutputs,
        block = block,
        confirmations = confirmations
      )

    (checkInputs *> checkOutputs *> checkFees).map(mkTxView)
  }

  def asMonadError[F[_]](
      id: String,
      hash: TxHash,
      receivedAt: Instant,
      lockTime: Long,
      fees: BigInt,
      inputs: Seq[InputView],
      outputs: Seq[OutputView],
      block: Option[BlockView],
      confirmations: Int
  )(implicit F: MonadError[F, Throwable]): F[TransactionView] = {
    val asValidatedNel: ValidatedNel[String, TransactionView] =
      TransactionView(id, hash, receivedAt, lockTime, fees, inputs, outputs, block, confirmations)
    F.fromValidated(
      asValidatedNel.leftMap(errors =>
        new IllegalArgumentException(
          s"Can not instantiate a transaction with hash ${hash.asString}:\n${errors.toList.map("- " + _).mkString("\n")}"
        )
      )
    )
  }
}
