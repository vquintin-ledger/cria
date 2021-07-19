package co.ledger.cria.domain.adapters.persistence.tee

import co.ledger.cria.domain.services.interpreter.{FlaggingService, OperationService, PersistenceFacade, PostSyncCheckService, TransactionService, WDService}

final class PersistenceFacadeTee(primary: PersistenceFacade, secondary: PersistenceFacade, combiner: Combiner) extends PersistenceFacade {
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
