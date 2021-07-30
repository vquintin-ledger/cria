package co.ledger.cria.domain.models.keychain

import co.ledger.cria.domain.models.interpreter.Derivation

case class AccountAddress(
    accountAddress: String,
    changeType: ChangeType,
    derivation: Derivation
)
