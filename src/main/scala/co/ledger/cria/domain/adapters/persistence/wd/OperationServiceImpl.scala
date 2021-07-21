package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.IO
import co.ledger.cria.domain.adapters.persistence.wd.models.WDOperationToSave
import co.ledger.cria.domain.adapters.persistence.wd.queries.OperationQueries
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{Operation, TransactionAmounts}
import co.ledger.cria.domain.services.interpreter.OperationService
import co.ledger.cria.logging.ContextLogging
import doobie._
import doobie.implicits._

class OperationServiceImpl(
    db: Transactor[IO]
) extends ContextLogging
    with OperationService {

  override def deleteUnconfirmedOperations(accountId: AccountUid): IO[Int] =
    OperationQueries
      .deleteUnconfirmedOperations(accountId)
      .transact(db)

  override def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    OperationQueries.removeFromCursor(accountId, blockHeight).transact(db)

  override def getUncomputedOperations(
      accountId: AccountUid,
      sort: Sort
  ): fs2.Stream[IO, TransactionAmounts] =
    OperationQueries
      .fetchUncomputedTransactionAmounts(accountId, sort)
      .transact(db)

  override def saveOperations(operations: List[Operation]): IO[Int] = {

    OperationQueries.saveOperations(operations.map(WDOperationToSave.fromOperation)).transact(db)
  }
}
