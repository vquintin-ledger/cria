package co.ledger.cria.domain.services.interpreter

import cats.data.NonEmptyList
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockHeight, TransactionAmounts, TransactionView}
import co.ledger.cria.domain.models.keychain.AccountAddress

trait OperationComputationService[F[_]] {
  def flagInputsAndOutputs(
      accountId: AccountUid,
      accountAddresses: List[AccountAddress]
  ): F[Unit]

  def getUncomputedOperations(
      accountId: AccountUid,
      sort: Sort,
      fromBlockHeight: Option[BlockHeight]
  ): fs2.Stream[F, TransactionAmounts]

  def fetchTransactions(
      accountId: AccountUid,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): fs2.Stream[F, TransactionView]
}
