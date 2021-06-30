package co.ledger.cria.itutils.models

import java.time.Instant
import cats.data.NonEmptyList
import co.ledger.cria.domain.models.keychain.ChangeType

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
