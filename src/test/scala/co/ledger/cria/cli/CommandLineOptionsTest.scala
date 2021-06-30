package co.ledger.cria.cli

import co.ledger.cria.domain.models.account.Coin

import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import co.ledger.cria.domain.models.interpreter.SyncId
import co.ledger.cria.domain.models.keychain.KeychainId
import org.scalatest.matchers.should.Matchers

class CommandLineOptionsTest extends AnyFlatSpec with Matchers {
  it should "parse successfully" in {
    val keychainId = KeychainId(UUID.randomUUID())
    val coin       = Coin.Btc
    val syncId     = SyncId(UUID.randomUUID())
    val hash       = "00000000000000000004bd803d1489f7df4d139987ed2ee761d0eb0726d2c088"
    val walletUid  = UUID.randomUUID()
    val rawArgs: List[String] = List(
      "--keychainId",
      keychainId.value.toString,
      "--coin",
      coin.toString,
      "--syncId",
      syncId.value.toString,
      "--blockHash",
      hash,
      "--walletUid",
      walletUid.toString
    )
    val expected = Right(CommandLineOptions(keychainId, coin, syncId, Some(hash), walletUid))

    val actual = CommandLineOptions.command.parse(rawArgs)

    actual shouldBe expected
  }
}
