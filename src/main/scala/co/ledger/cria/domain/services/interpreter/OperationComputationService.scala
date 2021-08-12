package co.ledger.cria.domain.services.interpreter

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockHeight, TransactionAmounts, TransactionView}
import co.ledger.cria.domain.models.keychain.AccountAddress

trait OperationComputationService {

  def flagInputsAndOutputs(
      accountId: AccountUid,
      accountAddresses: List[AccountAddress]
  ): IO[Unit]

  def getUncomputedOperations(
      accountId: AccountUid,
      sort: Sort,
      fromBlockHeight: Option[BlockHeight]
  ): fs2.Stream[IO, TransactionAmounts]

  def fetchTransactions(
      accountId: AccountUid,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): fs2.Stream[IO, TransactionView]

}
