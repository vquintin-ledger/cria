package co.ledger.cria.itutils.models

case class GetUtxosResult(
    utxos: List[ConfirmedUtxo],
    total: Int,
    truncated: Boolean
)
