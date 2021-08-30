package co.ledger.cria.domain.adapters.persistence.lama.models

import java.time.Instant
import co.ledger.cria.domain.models._
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockHeight, Operation, OperationType, Satoshis}

case class OperationToSave(
    uid: Operation.UID,
    accountId: AccountUid,
    hash: TxHash,
    operationType: OperationType,
    value: Satoshis,
    fees: Satoshis,
    time: Instant,
    blockHash: Option[String],
    blockHeight: Option[BlockHeight]
)

object OperationToSave {
  def fromOperation(op: Operation): OperationToSave =
    new OperationToSave(
      op.uid,
      op.accountId,
      op.hash,
      op.operationType,
      op.amount,
      op.fees,
      op.time,
      op.transaction.block.map(_.hash.asString),
      op.blockHeight
    )
}
