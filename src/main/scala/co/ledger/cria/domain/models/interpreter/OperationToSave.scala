package co.ledger.cria.domain.models.interpreter

import java.time.Instant
import co.ledger.cria.domain.models._
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.AccountUid

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

case class TransactionAmounts(
    accountId: AccountUid,
    hash: TxHash,
    blockHash: Option[String],
    blockHeight: Option[Long],
    blockTime: Option[Instant],
    fees: BigInt,
    inputAmount: BigInt,
    outputAmount: BigInt,
    changeAmount: BigInt
) extends ContextLogging {

  def computeOperations(implicit lc: CriaLogContext): List[OperationToSave] = {
    TransactionType.fromAmounts(inputAmount, outputAmount, changeAmount) match {
      case SendType =>
        List(makeOperationToSave(inputAmount - changeAmount, OperationType.Send))
      case ReceiveType =>
        List(makeOperationToSave(outputAmount + changeAmount, OperationType.Receive))
      case ChangeOnlyType =>
        List(makeOperationToSave(changeAmount, OperationType.Receive))
      case BothType =>
        List(
          makeOperationToSave(inputAmount - changeAmount, OperationType.Send),
          makeOperationToSave(outputAmount, OperationType.Receive)
        )
      case NoneType =>
        log
          .error(
            s"Error on tx : $hash, no transaction type found for amounts : input: $inputAmount, output: $outputAmount, change: $changeAmount"
          )
          .unsafeRunSync()
        Nil

    }
  }

  private def makeOperationToSave(amount: BigInt, operationType: OperationType) = {
    OperationToSave(
      Operation
        .uid(accountId, hash, operationType, blockHeight),
      accountId = accountId,
      hash = hash,
      operationType = operationType,
      value = amount,
      time = blockTime.getOrElse(Instant.now()),
      blockHash = blockHash,
      blockHeight = blockHeight,
      fees = fees
    )
  }
}
