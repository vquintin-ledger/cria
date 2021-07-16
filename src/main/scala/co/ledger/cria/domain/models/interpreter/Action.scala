package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.adapters.wd.models.WDTxToSave

sealed trait Action
case class Save(tx: WDTxToSave)   extends Action
case class Delete(tx: WDTxToSave) extends Action
