package co.ledger.cria.domain.services.interpreter

trait PersistenceFacade {
  def transactionRecordRepository: TransactionRecordRepository

  def operationComputationService: OperationComputationService

  def postSyncCheckService: PostSyncCheckService

  def operationRepository: OperationRepository
}
