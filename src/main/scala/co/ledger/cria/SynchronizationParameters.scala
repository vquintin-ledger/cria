package co.ledger.cria

import co.ledger.cria.models.keychain.AccountKey.Xpub
import java.util.UUID

import co.ledger.cria.models.account.{Coin, Scheme}

case class SynchronizationParameters(
    xpub: Xpub,
    scheme: Scheme,
    coin: Coin,
    syncId: UUID,
    blockHash: Option[String],
    walletUid: UUID,
    lookahead: Int
)
