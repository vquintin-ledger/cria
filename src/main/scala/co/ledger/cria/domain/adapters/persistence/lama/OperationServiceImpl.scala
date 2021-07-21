package co.ledger.cria.domain.adapters.persistence.lama

import cats.effect.IO
import co.ledger.cria.domain.adapters.persistence.lama.models.OperationToSave
import co.ledger.cria.domain.adapters.persistence.lama.queries.OperationQueries
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.Operation
import co.ledger.cria.domain.services.interpreter.OperationService
import co.ledger.cria.logging.ContextLogging
import doobie._
import doobie.implicits._

class OperationServiceImpl(
    db: Transactor[IO]
) extends OperationService
    with ContextLogging {

  def deleteUnconfirmedOperations(accountId: AccountUid): IO[Int] =
    OperationQueries
      .deleteUnconfirmedOperations(accountId)
      .transact(db)

  def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    OperationQueries.removeFromCursor(accountId, blockHeight).transact(db)

  def getUncomputedOperations(accountId: AccountUid, sort: Sort) =
    OperationQueries
      .fetchUncomputedTransactionAmounts(accountId, sort)
      .transact(db)

  def saveOperations(operations: List[Operation]) = {
    val opsToSave = operations.map(OperationToSave.fromOperation)
    OperationQueries.saveOperations(opsToSave).transact(db)
  }
}
