package co.ledger.cria.domain.adapters.wd.models

case class WDTxToSave(block: Option[WDBlock], tx: WDTransaction, ops: List[WDOperation])
