package co.ledger.lama.bitcoin.worker.services

import cats.effect.IO
import co.ledger.lama.bitcoin.worker.models.OperationToSave
import co.ledger.lama.common.logging.ContextLogging
import doobie._
import doobie.implicits._

import java.util.UUID

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
