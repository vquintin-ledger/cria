package co.ledger.cria.domain.adapters.persistence.wd.models

import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{Operation, OperationType, Satoshis}

import java.time.Instant

case class WDOperationToSave(
    uid: Operation.UID,
    accountId: AccountUid,
    hash: TxHash,
    operationType: OperationType,
    value: Satoshis,
    fees: Satoshis,
    time: Instant,
    blockHash: Option[String],
    blockHeight: Option[Long]
)

object WDOperationToSave {
  def fromOperation(op: Operation): WDOperationToSave =
    new WDOperationToSave(
      op.uid,
      op.accountId,
      op.hash,
      op.operationType,
      op.amount,
      op.fees,
      op.time,
      op.transaction.block.map(_.hash.asString),
      op.blockHeight.map(_.value)
    )
}
