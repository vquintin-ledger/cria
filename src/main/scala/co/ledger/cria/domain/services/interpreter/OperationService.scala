package co.ledger.cria.domain.services.interpreter

import cats.effect.IO
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.logging.ContextLogging
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.OperationToSave
import doobie._
import doobie.implicits._

class OperationService(
    db: Transactor[IO]
) extends ContextLogging {

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

  def saveOperations(operations: List[OperationToSave]) =
    OperationQueries.saveOperations(operations).transact(db)
}
