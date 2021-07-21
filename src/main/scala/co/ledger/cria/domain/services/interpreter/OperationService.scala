package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{Operation, TransactionAmounts}

trait OperationService {

  def deleteUnconfirmedOperations(accountId: AccountUid): IO[Int]

  def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int]

  def getUncomputedOperations(accountId: AccountUid, sort: Sort): fs2.Stream[IO, TransactionAmounts]

  def saveOperations(operations: List[Operation]): IO[Int]
}
