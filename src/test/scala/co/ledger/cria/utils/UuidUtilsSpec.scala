package co.ledger.cria.utils

import co.ledger.cria.domain.models.account.{Account, AccountId, Coin, CoinFamily}
import co.ledger.cria.domain.models.keychain.KeychainId
import org.scalacheck.Gen
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UuidUtilsSpec extends AnyFunSuite with Matchers {

  test("uuid to bytes") {
    val uuids = Gen.listOfN(1000, Gen.uuid).sample.get
    uuids.foreach { uuid =>
      val bytes = UuidUtils.uuidToBytes(uuid)
      UuidUtils.bytesToUuid(bytes) should contain(uuid)
    }
  }

  test("account identifier to uuid") {
    val keychainId = KeychainId.fromString("281f7c1c-f92f-3144-a6b2-514d9a2080e4").get

    val accountId = AccountId.fromString("95e95f78-de95-344c-bb60-a935f3b30050").get

    Account(
      keychainId,
      CoinFamily.Bitcoin,
      Coin.Btc
    ).id shouldBe accountId
  }

}
