package co.ledger.cria.domain.models.interpreter

import cats.Monoid
import cats.MonadError
import cats.Order
import cats.implicits._
import co.ledger.cria.domain.models.interpreter.Satoshis.{fromBigInt, fromBigIntUnsafe}
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined._
import eu.timepit.refined.api.Refined

final case class Satoshis(refined: Refined[BigInt, NonNegative]) {
  def asBigInt: BigInt = refined.value

  def +(rhs: Satoshis): Satoshis = fromBigIntUnsafe(asBigInt + rhs.asBigInt)

  def -(rhs: Satoshis): Option[Satoshis] = fromBigInt(asBigInt - rhs.asBigInt).toOption
}

object Satoshis {
  implicit val orderSatoshis: Order[Satoshis] =
    Order.by(_.asBigInt)

  def fromBigInt(n: BigInt): Either[String, Satoshis] =
    refineV[NonNegative](n).map(Satoshis(_))

  def asMonadError[F[_]](n: BigInt)(implicit F: MonadError[F, Throwable]): F[Satoshis] =
    F.fromEither(
      fromBigInt(n).leftMap(s => new IllegalArgumentException(s"Invalid satoshi amount: $s"))
    )

  private def fromBigIntUnsafe(n: BigInt): Satoshis =
    asMonadError[Either[Throwable, *]](n).fold(throw _, identity)

  val zero: Satoshis = fromBigIntUnsafe(0)

  implicit val satoshisMonoid: Monoid[Satoshis] =
    new Monoid[Satoshis] {
      override def empty: Satoshis = zero

      override def combine(x: Satoshis, y: Satoshis): Satoshis = x + y
    }
}
