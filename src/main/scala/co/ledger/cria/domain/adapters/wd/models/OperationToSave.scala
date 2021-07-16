package co.ledger.cria.domain.adapters.wd.models

import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{Operation, OperationType}

import java.time.Instant

case class OperationToSave(
                            uid: Operation.UID,
                            accountId: AccountUid,
                            hash: TxHash,
                            operationType: OperationType,
                            value: BigInt,
                            fees: BigInt,
                            time: Instant,
                            blockHash: Option[String],
                            blockHeight: Option[Long]
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