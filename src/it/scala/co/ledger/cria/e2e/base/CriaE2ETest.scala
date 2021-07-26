package co.ledger.cria.e2e.base

import cats.effect.ExitCode
import co.ledger.cria.App
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.e2e.{E2EHelper, KeychainHelper, RegisterRequest, TestCase}
import co.ledger.cria.itutils.ContainerSpec
import co.ledger.cria.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CriaE2ETest
    extends AnyFlatSpec
    with ContainerSpec
    with KeychainHelper
    with E2EHelper
    with Matchers {

  readTestCases().foreach { tc =>
    val request = tc.registerRequest

    s"${request.accountKey.extendedPublicKey}" should "perform correct sync on account" in IOAssertion {

      for {
        _          <- setupDB
        _          <- setupAccount(request)
        keychainId <- makeKeychainId(request)
        args = makeArgs(request, keychainId)
        exitCode <- App.run(args, conf)
        actual   <- getSyncResult(request.accountUid, keychainId, request.coin)
      } yield {
        withClue(s"perform correct sync on account") {
          exitCode shouldBe ExitCode.Success
        }

        withClue(s"Balance should be ${tc.expected.balance}") {
          actual.balance shouldBe tc.expected.balance
        }

        withClue(s"have ${tc.expected.opsSize} operations") {
          actual.opsSize shouldBe tc.expected.opsSize
        }

        withClue(s"have ${tc.expected.utxosSize} utxos") {
          actual.utxosSize shouldBe tc.expected.utxosSize
        }

        withClue(s"have received ${tc.expected.amountReceived} in total") {
          actual.amountReceived shouldBe tc.expected.amountReceived
        }

        withClue(s"have sent ${tc.expected.amountSent} in total") {
          actual.amountSent shouldBe tc.expected.amountSent
        }
      }
    }
  }

  def readTestCases(): List[TestCase] =
    List(
      "test-accounts-btc.json",
      // TODO Uncomment me when the explorers are stable (maybe ?)
      //"test-accounts-btc_testnet.json",
      "test-accounts-ltc.json"
    ).flatMap(TestCase.readJson)

  def makeArgs(request: RegisterRequest, keychainId: KeychainId): List[String] =
    List(
      ("--keychainId", keychainId.value.toString),
      ("--coin", request.coin),
      ("--syncId", request.syncId),
      ("--accountUid", request.accountUid),
      ("--walletUid", request.walletUid)
    ).flatMap { case (name, arg) => List(name, arg.toString) }
}
