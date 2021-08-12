package co.ledger.cria.domain.models.interpreter

import cats.implicits._
import cats.Order
import co.ledger.cria.domain.models.interpreter.BlockHeight.fromLongUnsafe
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._

case class BlockHeight(height: Refined[Long, NonNegative]) {

  def value: Long = height.value

  def succ: BlockHeight = fromLongUnsafe(value + 1)
}

object BlockHeight {
  implicit val orderBlockHeight: Order[BlockHeight] = Order.by(_.height.value)

  implicit val ordering: Ordering[BlockHeight] = orderBlockHeight.toOrdering

  def fromLong(height: Long): Either[String, BlockHeight] = {
    val h = refineV[NonNegative].apply[Long](height)
    h.map(BlockHeight.apply)
  }

  def fromLongUnsafe(height: Long): BlockHeight =
    fromLong(height).fold[BlockHeight](
      s => throw new IllegalArgumentException(s"Not a valid height: $s"),
      identity
    )

  val genesis: BlockHeight = BlockHeight(refineMV[NonNegative](0L))
}
