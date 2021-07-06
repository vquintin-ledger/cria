package co.ledger.cria.domain.models

import co.ledger.cria.domain.models.interpreter.{BlockHash, Coin, SyncId}
import co.ledger.cria.domain.models.keychain.KeychainId

import java.util.UUID

case class SynchronizationParameters(
    keychainId: KeychainId,
    coin: Coin,
    syncId: SyncId,
    blockHash: Option[BlockHash],
    walletUid: UUID
)
