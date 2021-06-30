package co.ledger.cria.domain.models.account

import io.circe.{Decoder, Encoder}
abstract class CoinFamily(val name: String) {
  override def toString: String = name
}

object CoinFamily {
  case object Bitcoin extends CoinFamily("bitcoin") {}

  val all: Map[String, CoinFamily] = Map(Bitcoin.name -> Bitcoin)

  def fromKey(key: String): Option[CoinFamily] = all.get(key)

  implicit val encoder: Encoder[CoinFamily] =
    Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[CoinFamily] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as coin family"))
}
