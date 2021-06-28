package co.ledger.cria.cli

import org.scalatest.flatspec.AnyFlatSpec
import java.util.UUID

import co.ledger.cria.models.account.Coin

class CommandLineOptionsTest extends AnyFlatSpec {
  it should "parse successfully" in {
    val keychainId = UUID.randomUUID()
    val coin       = Coin.Btc
    val syncId     = UUID.randomUUID()
    val hash       = "00000000000000000004bd803d1489f7df4d139987ed2ee761d0eb0726d2c088"
    val walletUid  = UUID.randomUUID()
    val rawArgs: List[String] = List(
      "--keychainId",
      keychainId.toString,
      "--coin",
      coin.toString,
      "--syncId",
      syncId.toString,
      "--blockHash",
      hash,
      "--walletUid",
      walletUid.toString
    )
    val expected = Right(CommandLineOptions(keychainId, coin, syncId, Some(hash), walletUid))

    val actual = CommandLineOptions.command.parse(rawArgs)

    assert(actual == expected)
  }
}
