package co.ledger.cria.cli

import co.ledger.cria.models.keychain.AccountKey.Xpub
import org.scalatest.flatspec.AnyFlatSpec
import java.util.UUID

import co.ledger.cria.models.account.{Coin, Scheme}

class CommandLineOptionsTest extends AnyFlatSpec {
  it should "parse successfully" in {
    val xpub = Xpub(
      "xpub6CUGRUonZSQ4TWtTMmzXdrXDtypWKiKrhko4egpiMZbpiaQL2jkwSB1icqYh2cfDfVxdx4df189oLKnC5fSwqPfgyP3hooxujYzAu3fDVmz"
    )
    val scheme    = Scheme.Bip44
    val coin      = Coin.Btc
    val syncId    = UUID.randomUUID()
    val hash      = "00000000000000000004bd803d1489f7df4d139987ed2ee761d0eb0726d2c088"
    val walletUid = UUID.randomUUID()
    val rawArgs: List[String] = List(
      "--xpub",
      xpub.extendedPublicKey,
      "--scheme",
      scheme.name,
      "--coin",
      coin.toString,
      "--syncId",
      syncId.toString,
      "--blockHash",
      hash,
      "--walletUid",
      walletUid.toString
    )
    val expected = Right(CommandLineOptions(xpub, scheme, coin, syncId, Some(hash), walletUid, 20))

    val actual = CommandLineOptions.command.parse(rawArgs)

    assert(actual == expected)
  }
}
