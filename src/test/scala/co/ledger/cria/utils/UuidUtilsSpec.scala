package co.ledger.cria.utils

import co.ledger.cria.domain.models.account.{Account, AccountId}
import co.ledger.cria.domain.models.interpreter.Coin
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

    val accountId = AccountId.fromString("c4097dda-70c6-3510-a60b-4509fc5a352e").get

    Account(
      keychainId,
      Coin.Btc
    ).id shouldBe accountId
  }

}
