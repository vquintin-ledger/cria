package co.ledger.cria.domain.models.interpreter

sealed trait Action
case class Save(tx: TransactionAmounts)   extends Action
case class Delete(tx: TransactionAmounts) extends Action
