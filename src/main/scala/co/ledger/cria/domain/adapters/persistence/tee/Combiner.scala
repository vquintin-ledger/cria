package co.ledger.cria.domain.adapters.persistence.tee

import cats.arrow.FunctionK
import cats.effect.{ContextShift, IO}
import cats.~>

trait Combiner {
  def combineAction[A](l: IO[A], r: IO[A]): IO[A]

  def combineStream[A](l: fs2.Stream[IO, A], r: fs2.Stream[IO, A]): fs2.Stream[IO, A]

  def combinePipe[A, B](l: fs2.Pipe[IO, A, B], r: fs2.Pipe[IO, A, B]): fs2.Pipe[IO, A, B] =
    (a: fs2.Stream[IO, A]) => combineStream(l.apply(a), r.apply(a))
}

object Combiner {
  type Pair[A] = (A, A)
  type SideEffect[A] = IO[Unit]
  type OnDiff = Pair ~> SideEffect

  def parallel(onDiff: OnDiff)(implicit cs: ContextShift[IO]): Combiner =
    new Combiner {
      override def combineAction[A](l: IO[A], r: IO[A]): IO[A] =
        l.parProduct(r).flatMap{ case (leftA, rightA) => IO.whenA(leftA != rightA)(onDiff(leftA, rightA)).as(leftA) }

      override def combineStream[A](l: fs2.Stream[IO, A], r: fs2.Stream[IO, A]): fs2.Stream[IO, A] =
        l.map(Some(_)).zipAll[IO, Option[A], Option[A]](r.map(Some(_)))(None, None)
          .evalMap{ case (left, right) => IO.whenA(left != right)(onDiff(left, right)).as(left)}
          .unNone
    }

  val failOnDiff: OnDiff =
    new FunctionK[Pair, SideEffect] {
      override def apply[A](p: (A, A)): SideEffect[A] =
        IO.raiseError(new RuntimeException(
          s"""Values returned by the two persistence layers did not match.
             |Primary:   ${p._1}
             |Secondary: ${p._2}
             |""".stripMargin
        ))
    }
}