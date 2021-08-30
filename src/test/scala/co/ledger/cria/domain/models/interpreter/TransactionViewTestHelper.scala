package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.interpreter.TransactionView.asMonadError

import java.time.Instant

object TransactionViewTestHelper {
  def unsafe(
      id: String,
      hash: TxHash,
      receivedAt: Instant,
      lockTime: Long,
      fees: Satoshis,
      inputs: Seq[InputView],
      outputs: Seq[OutputView],
      block: Option[BlockView],
      confirmations: Int
  ): TransactionView =
    asMonadError[Either[Throwable, *]](
      id,
      hash,
      receivedAt,
      lockTime,
      fees,
      inputs,
      outputs,
      block,
      confirmations
    ).fold[TransactionView](throw _, identity)
}
