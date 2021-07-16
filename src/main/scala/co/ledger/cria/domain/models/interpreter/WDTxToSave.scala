package co.ledger.cria.domain.models.interpreter

case class WDTxToSave(block: Option[BlockView], tx: TransactionView, ops: List[OperationToSave])
