package co.ledger.cria.domain.models.account

import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.models.keychain.KeychainId

case class Account(accountUid: AccountUid, identifier: KeychainId, coin: Coin)
