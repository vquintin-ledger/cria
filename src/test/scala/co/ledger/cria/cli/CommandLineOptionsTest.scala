package co.ledger.cria.cli

import java.util.UUID

import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import org.scalatest.flatspec.AnyFlatSpec
import co.ledger.cria.domain.models.interpreter.{BlockHash, Coin, SyncId}
import co.ledger.cria.domain.models.keychain.KeychainId
import org.scalatest.matchers.should.Matchers

class CommandLineOptionsTest extends AnyFlatSpec with Matchers {
  it should "parse successfully" in {
    val keychainId = KeychainId(UUID.randomUUID())
    val coin       = Coin.Btc
    val syncId     = SyncId(UUID.randomUUID())
    val hash =
      BlockHash.fromStringUnsafe("00000000000000000004bd803d1489f7df4d139987ed2ee761d0eb0726d2c088")
    val accountUid = AccountUid(UUID.randomUUID().toString)
    val walletUid  = WalletUid(UUID.randomUUID().toString)
    val rawArgs: List[String] = List(
      "--keychainId",
      keychainId.value.toString,
      "--coin",
      coin.toString,
      "--syncId",
      syncId.value.toString,
      "--blockHash",
      hash.asString,
      "--accountUid",
      accountUid.value,
      "--walletUid",
      walletUid.value
    )
    val expected =
      Right(CommandLineOptions(keychainId, coin, syncId, Some(hash), accountUid, walletUid))

    val actual = CommandLineOptions.command.parse(rawArgs)

    actual shouldBe expected
  }
}
