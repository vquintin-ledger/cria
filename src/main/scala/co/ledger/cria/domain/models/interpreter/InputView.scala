package co.ledger.cria.domain.models.interpreter

import cats.data.NonEmptyList

case class InputView(
    outputHash: String,
    outputIndex: Int,
    inputIndex: Int,
    value: BigInt,
    address: String,
    scriptSignature: String,
    txinwitness: List[String],
    sequence: Long,
    derivation: Option[NonEmptyList[Int]]
) {
  val belongs: Boolean = derivation.isDefined
}
