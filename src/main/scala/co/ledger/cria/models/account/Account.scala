package co.ledger.cria.models.account

import java.util.UUID

import io.circe.generic.extras.semiauto._
import co.ledger.cria.models.circeImplicits._
import io.circe.{Decoder, Encoder}

case class Account(identifier: String, coinFamily: CoinFamily, coin: Coin) {
  lazy val id: UUID = UUID.nameUUIDFromBytes((identifier + coinFamily + coin).getBytes)
}

object Account {
  implicit val encoder: Encoder[Account] = deriveConfiguredEncoder[Account]
  implicit val decoder: Decoder[Account] = deriveConfiguredDecoder[Account]
}
