package co.ledger.cria.domain.models

import cats.Eq
import cats.effect.IO
//import cats.implicits._

object IOEq {
  implicit def ioEq[A](implicit ev: Eq[A]): Eq[IO[A]] =
    Eq.by(fa => fa/*.flatTap(a => IO(println(a)))*/.unsafeRunSync())
}
