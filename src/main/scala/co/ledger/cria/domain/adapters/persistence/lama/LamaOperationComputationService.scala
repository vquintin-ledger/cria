package co.ledger.cria.domain.adapters.persistence.lama

import cats.data.NonEmptyList
import cats.implicits._
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.lama.queries.{
  LamaOperationQueries,
  LamaTransactionQueries
}
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockView, TransactionView}
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import co.ledger.cria.domain.services.interpreter.OperationComputationService
import co.ledger.cria.logging.ContextLogging
import doobie._
import doobie.implicits._
import fs2.Stream

class LamaOperationComputationService(
    db: Transactor[IO]
)(implicit cs: ContextShift[IO])
    extends OperationComputationService
    with ContextLogging {

  override def getUncomputedOperations(
      accountId: AccountUid,
      sort: Sort,
      fromBlockHeight: Option[Long]
  ) =
    LamaOperationQueries
      .fetchUncomputedTransactionAmounts(accountId, sort, fromBlockHeight)
      .transact(db)

  override def fetchTransactions(
      accountId: AccountUid,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): Stream[IO, TransactionView] =
    LamaTransactionQueries
      .fetchTransactions(accountId, sort, hashes)
      .flatMap(row =>
        LamaTransactionQueries
          .fetchTransactionDetails(accountId, sort, NonEmptyList.one(row.hash))
          .map((row, _))
      )
      .map { case (row, details) =>
        TransactionView(
          id = row.id,
          hash = row.hash,
          receivedAt = row.receivedAt,
          lockTime = row.lockTime,
          fees = row.fees,
          inputs = details.inputs,
          outputs = details.outputs,
          block = (row.blockHash, row.blockHeight, row.blockTime).mapN(BlockView(_, _, _)),
          confirmations = row.confirmations
        )
      }
      .transact(db)

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
          LamaOperationQueries
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
          LamaOperationQueries
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
          LamaOperationQueries
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
