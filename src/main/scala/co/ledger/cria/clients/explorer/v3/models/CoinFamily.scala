package co.ledger.cria.clients.explorer.v3.models

import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

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

  implicit val configReader: ConfigReader[CoinFamily] =
    ConfigReader.fromString(str =>
      fromKey(str).toRight(CannotConvert(str, "CoinFamily", "unknown"))
    )
}
