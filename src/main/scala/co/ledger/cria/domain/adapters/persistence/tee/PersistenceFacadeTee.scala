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

final class PersistenceFacadeTee private (
    primary: PersistenceFacade,
    secondary: PersistenceFacade,
    combiner: Combiner
) extends PersistenceFacade {
  override val transactionRecordRepository: TransactionRecordRepository =
    new TransactionRecordRepositoryTee(
      primary.transactionRecordRepository,
      secondary.transactionRecordRepository,
      combiner
    )

  override val operationComputationService: OperationComputationService =
    new OperationComputationServiceTee(
      primary.operationComputationService,
      secondary.operationComputationService,
      combiner
    )

  override val postSyncCheckService: PostSyncCheckService =
    new PostSyncCheckServiceTee(
      primary.postSyncCheckService,
      secondary.postSyncCheckService,
      combiner
    )

  override val operationRepository: OperationRepository =
    new OperationRepositoryTee(primary.operationRepository, secondary.operationRepository, combiner)
}

object PersistenceFacadeTee {
  def apply(
      primary: PersistenceFacade,
      secondary: PersistenceFacade,
      teeConfig: TeeConfig,
      log: IOLogger
  )(implicit lc: LogContext, cs: ContextShift[IO]): PersistenceFacade = {
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
