package co.ledger.cria.domain.adapters.persistence.tee

import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.services.interpreter.{FlaggingService, OperationService, PersistenceFacade, PostSyncCheckService, TransactionService, WDService}
import co.ledger.cria.logging.{IOLogger, LogContext}

final class PersistenceFacadeTee private (primary: PersistenceFacade, secondary: PersistenceFacade, combiner: Combiner) extends PersistenceFacade {
  override val transactionService: TransactionService =
    new TransactionServiceTee(primary.transactionService, secondary.transactionService, combiner)

  override val operationService: OperationService =
    new OperationServiceTee(primary.operationService, secondary.operationService, combiner)

  override val postSyncCheckService: PostSyncCheckService =
    new PostSyncCheckServiceTee(primary.postSyncCheckService, secondary.postSyncCheckService, combiner)

  override val flaggingService: FlaggingService =
    new FlaggingServiceTee(primary.flaggingService, secondary.flaggingService, combiner)

  override val wdService: WDService =
    new WDServiceTee(primary.wdService, secondary.wdService, combiner)
}

object PersistenceFacadeTee {
  def apply(primary: PersistenceFacade, secondary: PersistenceFacade, teeConfig: TeeConfig, log: IOLogger)(implicit lc: LogContext, cs: ContextShift[IO]): PersistenceFacade = {
    val onDiff = teeConfig.onDiff match {
      case OnDiffAction.Fail => Combiner.failOnDiff
      case OnDiffAction.Log => Combiner.logOnDiff(log)
    }

    val combiner = teeConfig.concurrency match {
      case TeeConcurrency.Parallel => Combiner.parallel(onDiff)
      case TeeConcurrency.Sequential => Combiner.sequential(onDiff)
    }

    new PersistenceFacadeTee(primary, secondary, combiner)
  }
}
