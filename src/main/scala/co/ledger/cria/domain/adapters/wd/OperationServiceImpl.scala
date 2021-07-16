package co.ledger.cria.domain.adapters.wd

import cats.effect.IO
import co.ledger.cria.domain.adapters.wd.queries.OperationQueries
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{OperationToSave, TransactionAmounts}
import co.ledger.cria.domain.services.interpreter.OperationService
import co.ledger.cria.logging.ContextLogging
import doobie._
import doobie.implicits._

class OperationServiceImpl(
                            db: Transactor[IO]
                          ) extends ContextLogging with OperationService {

  override def deleteUnconfirmedOperations(accountId: AccountUid): IO[Int] =
    OperationQueries
      .deleteUnconfirmedOperations(accountId)
      .transact(db)

  override def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int] =
    OperationQueries.removeFromCursor(accountId, blockHeight).transact(db)

  override def getUncomputedOperations(accountId: AccountUid, sort: Sort): fs2.Stream[IO, TransactionAmounts] =
    OperationQueries
      .fetchUncomputedTransactionAmounts(accountId, sort)
      .transact(db)

  override def saveOperations(operations: List[OperationToSave]): IO[Int] =
    OperationQueries.saveOperations(operations).transact(db)
}
