package co.ledger.cria.domain.adapters.persistence.lama

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.domain.services.interpreter.{
  OperationComputationService,
  PersistenceFacade,
  PostSyncCheckService,
  TransactionRecordRepository,
  OperationRepository
}
import co.ledger.cria.utils.{DbUtils, ResourceUtils}
import doobie.Transactor

final class LamaPersistenceFacade private (transactor: Transactor[IO], maxConcurrent: Int)(implicit
    cs: ContextShift[IO]
) extends PersistenceFacade {

  override val transactionRecordRepository: TransactionRecordRepository =
    new LamaTransactionRecordRepository(transactor, maxConcurrent)

  override val operationComputationService: OperationComputationService =
    new LamaOperationComputationService(transactor)

  override val postSyncCheckService: PostSyncCheckService =
    new LamaPostSyncCheckService(transactor)

  override val operationRepository: OperationRepository =
    new LamaOperationRepository(transactor)
}

object LamaPersistenceFacade {
  def apply(
      config: LamaDb
  )(implicit cs: ContextShift[IO], t: Timer[IO]): Resource[IO, PersistenceFacade] =
    for {
      _  <- migratedDatabase(config)
      db <- ResourceUtils.postgresTransactor(config.postgres)
    } yield new LamaPersistenceFacade(db, config.batchConcurrency.value)

  private def migratedDatabase(config: LamaDb): Resource[IO, Unit] =
    Resource
      .liftK[IO]
      .apply(DbUtils.flywayMigrate(config.postgres, "classpath:/db/lama_migration/"))
}
