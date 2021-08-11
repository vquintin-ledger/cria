package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.config.PostgresConfig
import co.ledger.cria.domain.services.interpreter.{
  OperationComputationService,
  OperationRepository,
  PersistenceFacade,
  PostSyncCheckService,
  TransactionRecordRepository
}
import co.ledger.cria.utils.{DbUtils, ResourceUtils}
import doobie.Transactor
import shapeless.tag
import shapeless.tag.@@

final class WDPersistenceFacade private (
    walletDaemonTransactor: Transactor[IO] @@ DBType.WD,
    temporaryTransactor: Transactor[IO] @@ DBType.Temporary,
    maxConcurrent: Int
)(implicit
    cs: ContextShift[IO]
) extends PersistenceFacade[IO] {

  override val transactionRecordRepository: TransactionRecordRepository[IO] =
    new WDTransactionRecordRepository(walletDaemonTransactor, temporaryTransactor, maxConcurrent)

  override val operationComputationService: OperationComputationService[IO] =
    new WDOperationComputationService(temporaryTransactor)

  override val postSyncCheckService: PostSyncCheckService[IO] =
    new WDPostSyncCheckService(walletDaemonTransactor)

  override val operationRepository: OperationRepository[IO] =
    new WDOperationRepository(walletDaemonTransactor)
}

object WDPersistenceFacade {
  def apply(
      db: WalletDaemonDb
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, PersistenceFacade[IO]] = {
    for {
      walletDaemonTransactor <- ResourceUtils.postgresTransactor(db.walletDaemon)
      _                      <- migratedCriaDB(db.criaExtra)
      extraTransactor        <- ResourceUtils.postgresTransactor(db.criaExtra)
    } yield new WDPersistenceFacade(
      tag[DBType.WD](walletDaemonTransactor),
      tag[DBType.Temporary](extraTransactor),
      db.batchConcurrency.value
    )
  }

  private def migratedCriaDB(config: PostgresConfig): Resource[IO, Unit] =
    Resource
      .liftK[IO]
      .apply(DbUtils.flywayMigrate(config, "classpath:/db/wd_temporary/"))
}
