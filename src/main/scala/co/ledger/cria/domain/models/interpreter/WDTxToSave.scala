package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.adapters.wd.models.WDOperation

case class WDTxToSave(block: Option[BlockView], tx: TransactionView, ops: List[WDOperation])
