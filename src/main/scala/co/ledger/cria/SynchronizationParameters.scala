package co.ledger.cria

import java.util.UUID

import co.ledger.cria.models.account.Coin

case class SynchronizationParameters(
    keychainId: UUID,
    coin: Coin,
    syncId: UUID,
    blockHash: Option[String],
    walletUid: UUID
)
