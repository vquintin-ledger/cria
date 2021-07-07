package co.ledger.cria.domain.models.interpreter

import cats.data.NonEmptyList
import co.ledger.cria.domain.models.keychain.ChangeType

case class OutputView(
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String,
    changeType: Option[ChangeType],
    derivation: Option[NonEmptyList[Int]]
) {
  val belongs: Boolean = derivation.isDefined
}
