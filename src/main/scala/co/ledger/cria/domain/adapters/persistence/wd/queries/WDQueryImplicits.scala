package co.ledger.cria.domain.adapters.persistence.wd.queries

import co.ledger.cria.domain.models.implicits._
import co.ledger.cria.domain.models.interpreter.{BlockHeight, Derivation, Satoshis}
import doobie._
import doobie.refined.implicits.refinedMeta
import doobie.postgres.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

object WDQueryImplicits {
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

  implicit val metaSatoshis: Meta[Satoshis] =
    refinedMeta[BigInt, NonNegative, Refined].timap(Satoshis(_))(_.refined)
}
