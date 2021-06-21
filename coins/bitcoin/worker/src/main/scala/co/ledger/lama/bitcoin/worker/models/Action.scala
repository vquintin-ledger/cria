package co.ledger.lama.bitcoin.worker.models

sealed trait Action
case class Save(tx: TransactionAmounts)   extends Action
case class Delete(tx: TransactionAmounts) extends Action
