package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.IO
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{Operation, TransactionAmounts}
import co.ledger.cria.domain.services.interpreter.OperationService

final class OperationServiceTee(primary: OperationService, secondary: OperationService, combiner: Combiner) extends OperationService {
  override def deleteUnconfirmedOperations(accountId: AccountUid): IO[Int] =
    combiner.combineAction(primary.deleteUnconfirmedOperations(accountId), secondary.deleteUnconfirmedOperations(accountId))

  override def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    combiner.combineAction(primary.removeFromCursor(accountId, blockHeight), secondary.removeFromCursor(accountId, blockHeight))

  override def getUncomputedOperations(accountId: AccountUid, sort: Sort): fs2.Stream[IO, TransactionAmounts] =
    combiner.combineStream(primary.getUncomputedOperations(accountId, sort), secondary.getUncomputedOperations(accountId, sort))

  override def saveOperations(operations: List[Operation]): IO[Int] =
    combiner.combineAction(primary.saveOperations(operations), secondary.saveOperations(operations))
}
