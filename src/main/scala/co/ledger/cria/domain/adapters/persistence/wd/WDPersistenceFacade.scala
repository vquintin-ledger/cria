package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.domain.services.interpreter.{
  OperationComputationService,
  PersistenceFacade,
  PostSyncCheckService,
  TransactionRecordRepository,
  OperationRepository
}
import co.ledger.cria.utils.ResourceUtils
import doobie.Transactor

final class WDPersistenceFacade private (transactor: Transactor[IO], maxConcurrent: Int)(implicit
    cs: ContextShift[IO]
) extends PersistenceFacade {

  override val transactionRecordRepository: TransactionRecordRepository =
    new WDTransactionRecordRepository(transactor, maxConcurrent)

  override val operationComputationService: OperationComputationService =
    new WDOperationComputationService(transactor)

  override val postSyncCheckService: PostSyncCheckService =
    new WDPostSyncCheckService(transactor)

  override val operationRepository: OperationRepository =
    new WDOperationRepository(transactor)
}

object WDPersistenceFacade {
  def apply(
      db: WalletDaemonDb
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): Resource[IO, PersistenceFacade] =
    ResourceUtils
      .postgresTransactor(db.postgres)
      .map(transactor => new WDPersistenceFacade(transactor, db.batchConcurrency.value))
}
