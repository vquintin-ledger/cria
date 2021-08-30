package co.ledger.cria.domain.models.interpreter

case class BlockchainBalance(
    balance: Satoshis,
    utxos: Int,
    received: Satoshis,
    netSent: Satoshis,
    fees: Satoshis
)

case class CurrentBalance(
    balance: Satoshis,
    utxos: Int,
    received: Satoshis,
    sent: Satoshis,
    // Actually a balance delta (can be negative)
    // TODO: change to an unconfirmed balance typed as Satoshis
    unconfirmedBalance: BigInt
)
