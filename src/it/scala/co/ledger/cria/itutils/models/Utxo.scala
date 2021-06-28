package co.ledger.cria.itutils.models

import java.time.Instant

import cats.data.NonEmptyList
import co.ledger.cria.models.interpreter.ChangeType

case class Utxo(
    transactionHash: String,
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String,
    changeType: Option[ChangeType],
    derivation: NonEmptyList[Int],
    time: Instant
)
