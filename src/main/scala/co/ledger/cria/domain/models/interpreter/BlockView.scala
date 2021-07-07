package co.ledger.cria.domain.models.interpreter

import java.time.Instant

case class BlockView(
    hash: BlockHash,
    height: Long,
    time: Instant
)

object BlockView {
  implicit val ordering: Ordering[BlockView] = Ordering.by(b => (b.height, b.time))
}
