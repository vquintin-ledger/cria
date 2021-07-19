package co.ledger.cria.domain.adapters.persistence.lama

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.domain.services.interpreter.{FlaggingService, OperationService, PersistenceFacade, PostSyncCheckService, TransactionService, WDService}
import co.ledger.cria.utils.{DbUtils, ResourceUtils}
import doobie.Transactor

final class PersistenceFacadeImpl private(transactor: Transactor[IO], maxConcurrent: Int) extends PersistenceFacade {
  override val transactionService: TransactionService =
    new TransactionServiceImpl(transactor, maxConcurrent)

  override val operationService: OperationService =
    new OperationServiceImpl(transactor)

  override val postSyncCheckService: PostSyncCheckService =
    new PostSyncCheckServiceImpl(transactor)

  override val flaggingService: FlaggingService =
    new FlaggingServiceImpl(transactor)

  override val wdService: WDService =
    new WDServiceImpl(transactor, transactionService)
}

object PersistenceFacadeImpl {
  def apply(config: LamaDb)(implicit cs: ContextShift[IO], t: Timer[IO]): Resource[IO, PersistenceFacade] =
    for {
      _ <- migratedDatabase(config)
      db <- ResourceUtils.postgresTransactor(config.postgres)
    } yield  new PersistenceFacadeImpl(db, config.batchConcurrency.value)

  private def migratedDatabase(config: LamaDb): Resource[IO, Unit] =
    Resource.liftK[IO].apply(DbUtils.flywayMigrate(config.postgres, "classpath:/db/lama_migration/"))
}