package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.TxHash

case class InputView(
    outputHash: TxHash,
    outputIndex: Int,
    inputIndex: Int,
    value: BigInt,
    address: String,
    scriptSignature: String,
    txinwitness: List[String],
    sequence: Long,
    derivation: Option[Derivation]
) {
  val belongs: Boolean = derivation.isDefined
}
