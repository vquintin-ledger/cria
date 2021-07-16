package co.ledger.cria.domain.models.interpreter

case class BlockchainBalance(
    balance: BigInt,
    utxos: Int,
    received: BigInt,
    netSent: BigInt,
    fees: BigInt
)

case class CurrentBalance(
    balance: BigInt,
    utxos: Int,
    received: BigInt,
    netSent: BigInt,
    fees: BigInt,
    unconfirmedBalance: BigInt
)
