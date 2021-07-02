package co.ledger.cria.domain.models

import co.ledger.cria.domain.models.interpreter.BlockView

sealed abstract class SynchronizationResult {
  def parameters: SynchronizationParameters
}
object SynchronizationResult {
  final case class SynchronizationSuccess(
      parameters: SynchronizationParameters,
      newCursor: BlockView
  ) extends SynchronizationResult

  final case class SynchronizationFailure(
      parameters: SynchronizationParameters,
      throwable: Throwable
  ) extends SynchronizationResult
}
