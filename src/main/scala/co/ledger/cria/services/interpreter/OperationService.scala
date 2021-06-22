package co.ledger.cria.services.interpreter

import java.util.UUID

import cats.effect.IO
import co.ledger.cria.logging.ContextLogging
import co.ledger.cria.models.interpreter.OperationToSave
import doobie._
import doobie.implicits._

class OperationService(
    db: Transactor[IO]
) extends ContextLogging {

  def deleteUnconfirmedOperations(accountId: UUID): IO[Int] =
    OperationQueries
      .deleteUnconfirmedOperations(accountId)
      .transact(db)

  def removeFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    OperationQueries.removeFromCursor(accountId, blockHeight).transact(db)

  def getUncomputedOperations(accountId: UUID) =
    OperationQueries
      .fetchUncomputedTransactionAmounts(accountId)
      .transact(db)

  def saveOperations(operations: List[OperationToSave]) =
    OperationQueries.saveOperations(operations).transact(db)
}
