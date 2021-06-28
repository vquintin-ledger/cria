package co.ledger.cria.services.interpreter

import java.time.Instant
import cats.data.NonEmptyList
import co.ledger.cria.models.interpreter.ChangeType

case class ConfirmedUtxo(
    height: Long,
    confirmations: Int,
    transactionHash: String,
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String,
    changeType: Option[ChangeType],
    derivation: NonEmptyList[Int],
    time: Instant
) {
  def toCommon: Utxo =
    Utxo(
      transactionHash,
      outputIndex,
      value,
      address,
      scriptHex,
      changeType,
      derivation,
      time
    )
}
