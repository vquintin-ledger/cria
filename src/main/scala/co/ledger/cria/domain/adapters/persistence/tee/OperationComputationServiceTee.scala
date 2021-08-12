package co.ledger.cria.domain.adapters.persistence.tee

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockHeight, TransactionAmounts, TransactionView}
import co.ledger.cria.domain.models.keychain.AccountAddress
import co.ledger.cria.domain.services.interpreter.OperationComputationService

final class OperationComputationServiceTee(
    primary: OperationComputationService,
    secondary: OperationComputationService,
    combiner: Combiner
) extends OperationComputationService {

  override def flagInputsAndOutputs(
      accountId: AccountUid,
      accountAddresses: List[AccountAddress]
  ): IO[Unit] =
    combiner.combineAction(
      primary.flagInputsAndOutputs(accountId, accountAddresses),
      secondary.flagInputsAndOutputs(accountId, accountAddresses)
    )

  override def getUncomputedOperations(
      accountId: AccountUid,
      sort: Sort,
      fromBlockHeight: Option[BlockHeight]
  ): fs2.Stream[IO, TransactionAmounts] =
    combiner.combineStream(
      primary.getUncomputedOperations(accountId, sort, fromBlockHeight),
      secondary.getUncomputedOperations(accountId, sort, fromBlockHeight)
    )

  override def fetchTransactions(
      accountId: AccountUid,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): fs2.Stream[IO, TransactionView] =
    combiner.combineStream(
      primary.fetchTransactions(accountId, sort, hashes),
      secondary.fetchTransactions(accountId, sort, hashes)
    )
}
