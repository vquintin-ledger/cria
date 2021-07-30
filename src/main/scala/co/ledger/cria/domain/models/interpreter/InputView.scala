package co.ledger.cria.domain.models.interpreter

case class InputView(
    outputHash: String,
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
