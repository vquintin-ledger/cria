package co.ledger.cria.domain.adapters.persistence.wd

import cats.implicits._
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.wd.queries.{
  WDOperationQueries,
  WDTransactionQueries
}
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{TransactionAmounts, TransactionView}
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import co.ledger.cria.domain.services.interpreter.OperationComputationService
import co.ledger.cria.logging.ContextLogging
import doobie._
import doobie.implicits._
import fs2.Stream

class WDOperationComputationService(
    db: Transactor[IO]
)(implicit cs: ContextShift[IO])
    extends ContextLogging
    with OperationComputationService {

  override def getUncomputedOperations(
      accountId: AccountUid,
      sort: Sort
  ): fs2.Stream[IO, TransactionAmounts] =
    WDOperationQueries
      .fetchUncomputedTransactionAmounts(accountId, sort)
      .transact(db)

  override def fetchTransactions(
      accountId: AccountUid,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): Stream[IO, TransactionView] = {
    val txStream = WDTransactionQueries.fetchTransaction(accountId, sort, hashes).transact(db)
    val txDetailsStream =
      WDTransactionQueries.fetchTransactionDetails(accountId, sort, hashes).transact(db)

    txStream
      .zip(txDetailsStream)
      .collect {
        case (tx, details) if tx.hash == details.txHash =>
          tx.copy(inputs = details.inputs, outputs = details.outputs)
      }

  }

  override def flagInputsAndOutputs(
      accountId: AccountUid,
      accountAddresses: List[AccountAddress]
  ): IO[Unit] = {
    val (internalAddresses, externalAddresses) =
      accountAddresses
        .partition(_.changeType == ChangeType.Internal)

    val flagInputs =
      NonEmptyList
        .fromList(accountAddresses)
        .map { addresses =>
          WDOperationQueries
            .flagBelongingInputs(
              accountId,
              addresses
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    // Flag outputs with known addresses and update address type (INTERNAL / EXTERNAL)
    val flagInternalOutputs =
      NonEmptyList
        .fromList(internalAddresses)
        .map { addresses =>
          WDOperationQueries
            .flagBelongingOutputs(
              accountId,
              addresses,
              ChangeType.Internal
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    val flagExternalOutputs =
      NonEmptyList
        .fromList(externalAddresses)
        .map { addresses =>
          WDOperationQueries
            .flagBelongingOutputs(
              accountId,
              addresses,
              ChangeType.External
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    (flagInputs, flagInternalOutputs, flagExternalOutputs).parTupled.void
  }

}
