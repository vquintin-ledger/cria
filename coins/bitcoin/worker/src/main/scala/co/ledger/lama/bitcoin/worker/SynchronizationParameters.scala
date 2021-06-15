package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.models.Scheme
import co.ledger.lama.bitcoin.common.models.keychain.AccountKey.Xpub
import co.ledger.lama.common.models.Coin

import java.util.UUID

case class SynchronizationParameters(
    xpub: Xpub,
    scheme: Scheme,
    coin: Coin,
    syncId: UUID,
    cursor: Option[String],
    walletId: UUID,
    lookahead: Int
)
