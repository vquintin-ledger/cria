package co.ledger.cria.domain.models.account

import java.util.UUID
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.models.keychain.KeychainId

case class Account(identifier: KeychainId, coin: Coin) {
  lazy val id: AccountId = AccountId(
    UUID.nameUUIDFromBytes((identifier.value.toString + coin).getBytes)
  )
}
