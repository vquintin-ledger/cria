package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.config.{DatabaseConfig, SqliteConfig}
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

import java.nio.file.Files

final class WDPersistenceFacade private (
    walletDaemonTransactor: Transactor[IO] @@ DBType.WD,
    temporaryTransactor: Transactor[IO] @@ DBType.Temporary,
    maxConcurrent: Int
)(implicit
    cs: ContextShift[IO]
) extends PersistenceFacade {

  override val transactionRecordRepository: TransactionRecordRepository =
    new WDTransactionRecordRepository(walletDaemonTransactor, temporaryTransactor, maxConcurrent)

  override val operationComputationService: OperationComputationService =
    new WDOperationComputationService(temporaryTransactor)

  override val postSyncCheckService: PostSyncCheckService =
    new WDPostSyncCheckService(walletDaemonTransactor)

  override val operationRepository: OperationRepository =
    new WDOperationRepository(walletDaemonTransactor)
}

object WDPersistenceFacade {
  def apply(
      db: WalletDaemonDb
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, PersistenceFacade] = {
    for {
      walletDaemonTransactor <- ResourceUtils.databaseTransactor(db.postgres)
      temporaryConfig        <- mkTemporaryDB
      _                      <- setUpTemporaryDB(temporaryConfig)
      temporaryTransactor    <- ResourceUtils.databaseTransactor(temporaryConfig)
    } yield new WDPersistenceFacade(
      tag[DBType.WD](walletDaemonTransactor),
      tag[DBType.Temporary](temporaryTransactor),
      db.batchConcurrency.value
    )
  }

  private def setUpTemporaryDB(config: DatabaseConfig): Resource[IO, Unit] =
    Resource
      .liftK[IO]
      .apply(DbUtils.flywayMigrate(config, "classpath:/db/wd_temporary/"))

  private def mkTemporaryDB: Resource[IO, DatabaseConfig] =
    Resource
      .make(IO(Files.createTempFile("wd_temporary_", ".sqlite")))(p => IO(p.toFile.delete()))
      .map { p =>
        SqliteConfig(url = s"jdbc:sqlite:${p.toString}")
      }
}
