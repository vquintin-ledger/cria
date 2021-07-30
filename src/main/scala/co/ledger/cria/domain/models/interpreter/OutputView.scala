package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.keychain.ChangeType

case class OutputView(
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String,
    changeType: Option[ChangeType],
    derivation: Option[Derivation]
) {
  val belongs: Boolean = derivation.isDefined
}
