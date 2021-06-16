package co.ledger.lama.bitcoin.worker

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import co.ledger.lama.common.models.implicits._

import java.util.UUID

case class SynchronizationParameters (xpub: String,
                                      syncId: UUID,
                                      cursor: Option[String],
                                      walletId: UUID)

object SynchronizationParameters {
  implicit def encoder: Encoder[SynchronizationParameters] =
    deriveConfiguredEncoder[SynchronizationParameters]
}
