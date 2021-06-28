package co.ledger.cria.services.interpreter

case class GetUtxosResult(
    utxos: List[ConfirmedUtxo],
    total: Int,
    truncated: Boolean
)
