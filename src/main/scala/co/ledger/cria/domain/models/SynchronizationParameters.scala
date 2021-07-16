package co.ledger.cria.domain.models

import co.ledger.cria.domain.models.interpreter.{BlockHash, Coin, SyncId}
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}

case class SynchronizationParameters(
    keychainId: KeychainId,
    coin: Coin,
    syncId: SyncId,
    blockHash: Option[BlockHash],
    accountUid: AccountUid,
    walletUid: WalletUid
)
