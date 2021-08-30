package co.ledger.cria.domain.models.interpreter

import cats.MonadError
import cats.implicits._

import java.time.Instant
import co.ledger.cria.domain.models._
import co.ledger.cria.logging.ContextLogging
import co.ledger.cria.domain.models.account.AccountUid

case class TransactionAmounts(
    accountId: AccountUid,
    hash: TxHash,
    blockHash: Option[String],
    blockHeight: Option[BlockHeight],
    blockTime: Option[Instant],
    fees: Satoshis,
    inputAmount: Satoshis,
    outputAmount: Satoshis,
    changeAmount: Satoshis
) extends ContextLogging {

  def computeOperations[F[_]](
      transaction: TransactionView
  )(implicit F: MonadError[F, Throwable]): F[List[Operation]] = {
    TransactionType.fromAmounts(inputAmount, outputAmount, changeAmount) match {
      case SendType =>
        netInput[F](inputAmount, changeAmount).map(netInput =>
          List(makeOperationToSave(netInput, OperationType.Send, transaction))
        )
      case ReceiveType =>
        F.pure(
          List(makeOperationToSave(outputAmount + changeAmount, OperationType.Receive, transaction))
        )
      case ChangeOnlyType =>
        F.pure(
          List(makeOperationToSave(changeAmount, OperationType.Receive, transaction))
        )
      case BothType =>
        netInput[F](inputAmount, changeAmount).map(netInput =>
          List(
            makeOperationToSave(netInput, OperationType.Send, transaction),
            makeOperationToSave(outputAmount, OperationType.Receive, transaction)
          )
        )
      case NoneType =>
        F.raiseError(
          new RuntimeException(
            s"Error on tx : $hash, no transaction type found for amounts : input: $inputAmount, output: $outputAmount, change: $changeAmount"
          )
        )
    }
  }

  private def netInput[F[_]](input: Satoshis, change: Satoshis)(implicit
      F: MonadError[F, Throwable]
  ): F[Satoshis] =
    F.fromOption(
      input - change,
      new RuntimeException(s"More change than input in transaction ${hash.asString}")
    )

  private def makeOperationToSave(
      amount: Satoshis,
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
