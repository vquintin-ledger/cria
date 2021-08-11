package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.services.interpreter.{
  OperationComputationService,
  PersistenceFacade,
  PostSyncCheckService,
  TransactionRecordRepository,
  OperationRepository
}
import co.ledger.cria.logging.{IOLogger, LogContext}

final class PersistenceFacadeTee[F[_]] private (
    primary: PersistenceFacade[F],
    secondary: PersistenceFacade[F],
    combiner: Combiner[F]
) extends PersistenceFacade[F] {
  override val transactionRecordRepository: TransactionRecordRepository[F] =
    new TransactionRecordRepositoryTee(
      primary.transactionRecordRepository,
      secondary.transactionRecordRepository,
      combiner
    )

  override val operationComputationService: OperationComputationService[F] =
    new OperationComputationServiceTee(
      primary.operationComputationService,
      secondary.operationComputationService,
      combiner
    )

  override val postSyncCheckService: PostSyncCheckService[F] =
    new PostSyncCheckServiceTee(
      primary.postSyncCheckService,
      secondary.postSyncCheckService,
      combiner
    )

  override val operationRepository: OperationRepository[F] =
    new OperationRepositoryTee(primary.operationRepository, secondary.operationRepository, combiner)
}

object PersistenceFacadeTee {
  def apply(
      primary: PersistenceFacade[IO],
      secondary: PersistenceFacade[IO],
      teeConfig: TeeConfig,
      log: IOLogger
  )(implicit lc: LogContext, cs: ContextShift[IO]): PersistenceFacade[IO] = {
    val onDiff = teeConfig.onDiff match {
      case OnDiffAction.Fail => Combiner.failOnDiff
      case OnDiffAction.Log  => Combiner.logOnDiff(log)
    }

    val combiner = teeConfig.concurrency match {
      case TeeConcurrency.Parallel   => Combiner.parallel(onDiff)
      case TeeConcurrency.Sequential => Combiner.sequential(onDiff)
    }

    new PersistenceFacadeTee(primary, secondary, combiner)
  }
}
