package co.ledger.cria.domain.models.interpreter

import cats.effect.IO
import io.circe.Decoder
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

sealed abstract class Coin(val name: String) {
  override def toString: String = name
}

object BitcoinLikeCoin {
  def fromKeyIO(key: String): IO[Coin] = IO.fromOption(Coin.fromKey(key) collect { case c: Coin =>
    c
  })(
    new IllegalArgumentException(
      s"Coin ${key} is not BitcoinLike, or unknown coin"
    )
  )
}

object Coin {
  case object Btc extends Coin("btc") {}

  case object BtcTestnet extends Coin("btc_testnet") {}

  case object BtcRegtest extends Coin("btc_regtest") {}

  case object Ltc extends Coin("ltc") {}

  val all: Map[String, Coin] = Map(
    Btc.name        -> Btc,
    BtcTestnet.name -> BtcTestnet,
    BtcRegtest.name -> BtcRegtest,
    Ltc.name        -> Ltc
  )

  def fromKey(key: String): Option[Coin] = all.get(key)

  def fromKeyIO(key: String): IO[Coin] = IO.fromOption(fromKey(key))(
    new IllegalArgumentException(
      s"Unknown coin type $key) in CreateTransactionRequest"
    )
  )

  // Used in e2e tests
  implicit val decoder: Decoder[Coin] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as coin"))

  implicit val configReader: ConfigReader[Coin] =
    ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Coin", "unknown")))
}
