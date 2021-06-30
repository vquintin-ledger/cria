package co.ledger.cria.domain.models.account

import co.ledger.cria.clients.explorer.types.{Coin, CoinFamily}

import java.util.UUID
import io.circe.generic.extras.semiauto._
import co.ledger.cria.domain.models.circeImplicits._
import co.ledger.cria.domain.models.keychain.KeychainId
import io.circe.{Decoder, Encoder}

case class Account(identifier: KeychainId, coinFamily: CoinFamily, coin: Coin) {
  lazy val id: AccountId = AccountId(
    UUID.nameUUIDFromBytes((identifier.value.toString + coinFamily + coin).getBytes)
  )
}

object Account {
  implicit val encoder: Encoder[Account] = deriveConfiguredEncoder[Account]
  implicit val decoder: Decoder[Account] = deriveConfiguredDecoder[Account]
}
