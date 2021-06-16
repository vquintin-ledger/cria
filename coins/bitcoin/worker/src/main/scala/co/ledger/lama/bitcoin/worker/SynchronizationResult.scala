package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.models.explorer.Block

sealed abstract class SynchronizationResult {
  def parameters: SynchronizationParameters
}
object SynchronizationResult {
  final case class SynchronizationSuccess(parameters: SynchronizationParameters, newCursor: Block) extends SynchronizationResult

  final case class SynchronizationFailure(parameters: SynchronizationParameters, throwable: Throwable) extends SynchronizationResult
}
