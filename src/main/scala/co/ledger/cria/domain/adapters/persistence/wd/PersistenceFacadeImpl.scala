package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.domain.services.interpreter.{
  FlaggingService,
  OperationService,
  PersistenceFacade,
  PostSyncCheckService,
  TransactionService,
  WDService
}
import co.ledger.cria.utils.ResourceUtils
import doobie.Transactor

final class PersistenceFacadeImpl private (transactor: Transactor[IO], maxConcurrent: Int)
    extends PersistenceFacade {

  override val transactionService: TransactionService =
    new TransactionServiceImpl(transactor, maxConcurrent)

  override val operationService: OperationService =
    new OperationServiceImpl(transactor)

  override val postSyncCheckService: PostSyncCheckService =
    new PostSyncCheckServiceImpl(transactor)

  override val flaggingService: FlaggingService =
    new FlaggingServiceImpl(transactor)

  override val wdService: WDService =
    new WDServiceImpl(transactor)
}

object PersistenceFacadeImpl {
  def apply(
      db: WalletDaemonDb
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, PersistenceFacade] =
    ResourceUtils
      .postgresTransactor(db.postgres)
      .map(transactor => new PersistenceFacadeImpl(transactor, db.batchConcurrency.value))
}
