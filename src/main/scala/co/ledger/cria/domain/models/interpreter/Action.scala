package co.ledger.cria.domain.models.interpreter

sealed trait Action
case class Save(op: Operation)   extends Action
case class Delete(op: Operation) extends Action
