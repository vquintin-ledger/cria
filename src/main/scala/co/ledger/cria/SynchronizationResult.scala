package co.ledger.cria

import co.ledger.cria.models.explorer.Block

sealed abstract class SynchronizationResult {
  def parameters: SynchronizationParameters
}
object SynchronizationResult {
  final case class SynchronizationSuccess(parameters: SynchronizationParameters, newCursor: Block)
      extends SynchronizationResult

  final case class SynchronizationFailure(
      parameters: SynchronizationParameters,
      throwable: Throwable
  ) extends SynchronizationResult
}
