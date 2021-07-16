package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.domain.services.interpreter.{FlaggingService, OperationService, PersistenceFacade, PostSyncCheckService, TransactionService, WDService}
import co.ledger.cria.utils.ResourceUtils
import doobie.Transactor

final class PersistenceFacadeImpl private (transactor: Transactor[IO], maxConcurrent: Int) extends PersistenceFacade {

  override def transactionService: TransactionService =
    new TransactionServiceImpl(transactor, maxConcurrent)

  override def operationService: OperationService =
    new OperationServiceImpl(transactor)

  override def postSyncCheckService: PostSyncCheckService =
    new PostSyncCheckServiceImpl(transactor)

  override def flaggingService: FlaggingService =
    new FlaggingServiceImpl(transactor)

  override def wdService: WDService =
    new WDServiceImpl(transactor)
}

object PersistenceFacadeImpl {
  def apply(db: Db)(implicit cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, PersistenceFacade] =
    ResourceUtils.postgresTransactor(db.postgres).map(transactor => new PersistenceFacadeImpl(transactor, db.batchConcurrency.value))
}