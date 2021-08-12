package co.ledger.cria.domain.adapters.persistence.lama.queries

import co.ledger.cria.domain.models.interpreter.{BlockHeight, Derivation}
import doobie.postgres.implicits._
import doobie.{Meta, _}
import doobie.refined.implicits.refinedMeta
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

object LamaQueryImplicits {

  implicit val metaDerivation: Meta[Derivation] = {
    val put = Put[List[Int]].contramap[Derivation](d => List(d.chain, d.addressNumber))
    val get = Get[List[Int]].temap {
      case chain :: addressNumber :: Nil => Right(Derivation(chain, addressNumber))
      case l                             => Left(s"$l is not a derivation")
    }
    new Meta(get, put)
  }

  implicit val metaBlockHeight: Meta[BlockHeight] =
    refinedMeta[Long, NonNegative, Refined].timap[BlockHeight](BlockHeight(_))(_.height)
}
