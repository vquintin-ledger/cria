package co.ledger.cria.domain.models.keychain

import cats.data.NonEmptyList

case class AccountAddress(
    accountAddress: String,
    changeType: ChangeType,
    derivation: NonEmptyList[Int]
)
