package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.TxHash

import java.time.Instant
case class TransactionView(
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
