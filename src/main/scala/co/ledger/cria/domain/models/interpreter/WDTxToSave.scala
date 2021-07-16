package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.adapters.wd.models.{WDOperation, WDTransaction}

case class WDTxToSave(block: Option[BlockView], tx: WDTransaction, ops: List[WDOperation])
