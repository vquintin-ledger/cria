package co.ledger.cria.domain.services.interpreter

trait PersistenceFacade {
  def transactionService: TransactionService

  def operationService: OperationService

  def postSyncCheckService: PostSyncCheckService

  def flaggingService: FlaggingService

  def wdService: WDService
}
