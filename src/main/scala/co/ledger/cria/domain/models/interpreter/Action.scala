package co.ledger.cria.domain.models.interpreter

sealed trait Action
case class Save(tx: WDTxToSave)   extends Action
case class Delete(tx: WDTxToSave) extends Action
