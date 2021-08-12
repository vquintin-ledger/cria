package co.ledger.cria.domain.models.interpreter

import java.time.Instant
import co.ledger.cria.domain.models._
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.AccountUid

case class TransactionAmounts(
    accountId: AccountUid,
    hash: TxHash,
    blockHash: Option[String],
    blockHeight: Option[BlockHeight],
    blockTime: Option[Instant],
    fees: BigInt,
    inputAmount: BigInt,
    outputAmount: BigInt,
    changeAmount: BigInt
) extends ContextLogging {

  def computeOperations(
      transaction: TransactionView
  )(implicit lc: CriaLogContext): List[Operation] = {
    TransactionType.fromAmounts(inputAmount, outputAmount, changeAmount) match {
      case SendType =>
        List(makeOperationToSave(inputAmount - changeAmount, OperationType.Send, transaction))
      case ReceiveType =>
        List(makeOperationToSave(outputAmount + changeAmount, OperationType.Receive, transaction))
      case ChangeOnlyType =>
        List(makeOperationToSave(changeAmount, OperationType.Receive, transaction))
      case BothType =>
        List(
          makeOperationToSave(inputAmount - changeAmount, OperationType.Send, transaction),
          makeOperationToSave(outputAmount, OperationType.Receive, transaction)
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

  private def makeOperationToSave(
      amount: BigInt,
      operationType: OperationType,
      transaction: TransactionView
  ) = {
    Operation(
      Operation
        .uid(accountId, hash, operationType, blockHeight),
      accountId = accountId,
      hash = hash,
      transaction = transaction,
      operationType = operationType,
      amount = amount,
      time = transaction.receivedAt,
      blockHeight = blockHeight,
      fees = fees
    )
  }
}
