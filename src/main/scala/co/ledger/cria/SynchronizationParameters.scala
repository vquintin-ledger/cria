package co.ledger.cria

import java.util.UUID
import co.ledger.cria.domain.models.interpreter.{Coin, SyncId}
import co.ledger.cria.domain.models.keychain.KeychainId

case class SynchronizationParameters(
    keychainId: KeychainId,
    coin: Coin,
    syncId: SyncId,
    blockHash: Option[String],
    walletUid: UUID
)
