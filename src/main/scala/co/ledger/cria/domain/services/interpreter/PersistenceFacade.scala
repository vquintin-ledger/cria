package co.ledger.cria.domain.services.interpreter

trait PersistenceFacade[F[_]] {
  def transactionRecordRepository: TransactionRecordRepository[F]

  def operationComputationService: OperationComputationService[F]

  def postSyncCheckService: PostSyncCheckService[F]

  def operationRepository: OperationRepository[F]
}
