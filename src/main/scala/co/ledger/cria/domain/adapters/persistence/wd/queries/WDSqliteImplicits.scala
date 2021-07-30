package co.ledger.cria.domain.adapters.persistence.wd.queries

import co.ledger.cria.domain.models.interpreter.Derivation
import doobie._
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import cats.implicits._

import java.time.Instant

object WDSqliteImplicits {
  implicit val metaDerivation: Meta[Derivation] = {
    val metaListInt = metaJson[List[Int]]
    val put         = metaListInt.put.contramap[Derivation](d => List(d.chain, d.addressNumber))
    val get = metaListInt.get.temap {
      case chain :: addressNumber :: Nil => Right(Derivation(chain, addressNumber))
      case l                             => Left(s"$l is not a derivation")
    }
    new Meta(get, put)
  }

  implicit val metaInstant: Meta[Instant] = metaJson[Instant]

  implicit def metaList[A: Encoder: Decoder]: Meta[List[A]] = metaJson[List[A]]

  private def metaJson[A: Encoder: Decoder]: Meta[A] = {
    val put = Put[String].contramap[A](a => a.asJson.noSpaces)
    val get = Get[String].temap[A](rawJson => decode[A](rawJson).leftMap(e => e.toString))
    new Meta[A](get, put)
  }
}
