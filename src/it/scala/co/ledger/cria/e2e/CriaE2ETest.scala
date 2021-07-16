package co.ledger.cria.e2e

import java.util.UUID

import cats.effect.{ExitCode, IO}
import co.ledger.cria.App
import co.ledger.cria.App.ClientResources
import co.ledger.cria.e2e.CriaE2ETest.{RegisterRequest, SyncResult, TestCase}
import co.ledger.cria.itutils.ContainerFlatSpec
import co.ledger.cria.itutils.TestUtils
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.clients.explorer.models.circeImplicits._
import co.ledger.cria.domain.models.account.{Account, AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.itutils.models.keychain.AccountKey.Xpub
import co.ledger.cria.itutils.models.keychain.{KeychainInfo, Scheme}
import io.circe.Decoder
import co.ledger.cria.itutils.models.keychain.CoinImplicits._
import co.ledger.cria.utils.IOAssertion
import co.ledger.protobuf.bitcoin.keychain
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import org.scalatest.matchers.should.Matchers
import io.circe.parser.decode
import io.grpc.Metadata

import scala.io.Source

class CriaE2ETest extends ContainerFlatSpec with Matchers {

  readTestCases().foreach { tc =>
    val request = tc.registerRequest

    s"${request.accountKey.extendedPublicKey}" should "perform correct sync on account" in IOAssertion {

      for {
        _          <- setup
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

  private def setupAccount(request: RegisterRequest): IO[Int] = {
    appResources.use { case ClientResources(_, _, db) =>
      val utils = new TestUtils(db)
      utils.setupAccount(AccountUid(request.accountUid), WalletUid(request.walletUid))
    }
  }

  def readTestCases(): List[TestCase] =
    List(
      "test-accounts-btc.json",
      // TODO Uncomment me when the explorers are stable (maybe ?)
      //"test-accounts-btc_testnet.json",
      "test-accounts-ltc.json"
    ).flatMap(readJson[List[TestCase]])

  def makeKeychainId(request: RegisterRequest): IO[KeychainId] =
    testResources.use { tr =>
      tr.rawKeychainClient
        .createKeychain(
          keychain.CreateKeychainRequest(
            request.accountKey.toProto,
            request.scheme.toProto,
            request.lookaheadSize,
            Some(request.coin.toNetwork.toKeychainChainParamsProto)
//            ,
//            request.accountIndex,
//            request.metadata
          ),
          new Metadata
        )
        .map(KeychainInfo.fromProto)
        .map(_.keychainId)
    }

  def makeArgs(request: RegisterRequest, keychainId: KeychainId): List[String] =
    List(
      ("--keychainId", keychainId.value.toString),
      ("--coin", request.coin),
      ("--syncId", request.syncId),
      ("--accountUid", request.accountUid),
      ("--walletUid", request.walletUid)
    ).flatMap { case (name, arg) => List(name, arg.toString) }

  def getSyncResult(accountUid: String, keychainId: KeychainId, coin: Coin): IO[SyncResult] =
    testResources.use { res =>
      val account = Account(AccountUid(accountUid), keychainId, coin)
      for {
        opsSize   <- res.testUtils.getOperationCount(account.accountUid)
        utxosSize <- res.testUtils.getUtxos(account.accountUid, 20, 0, Sort.Ascending)
        balance   <- res.testUtils.getBalance(account.accountUid)
      } yield SyncResult(
        opsSize,
        utxosSize.total,
        balance.balance.longValue,
        balance.received.longValue,
        balance.netSent.longValue + balance.fees.longValue
      )
    }

  private def readJson[A: Decoder](file: String): A = {
    val raw = Source.fromResource(file).getLines().foldLeft("")(_ + _)
    decode[A](raw)
      .fold(err => throw new IllegalArgumentException(s"Could not parse $file", err), identity)
  }
}

object CriaE2ETest {
  case class TestCase(registerRequest: RegisterRequest, expected: SyncResult)

  case class RegisterRequest(
      accountKey: Xpub,
      scheme: Scheme,
      lookaheadSize: Int,
      coin: Coin,
      syncId: UUID,
      accountUid: String,
      walletUid: String,
      accountIndex: Int,
      metadata: String
  )

  case class SyncResult(
      opsSize: Int,
      utxosSize: Int,
      balance: Long,
      amountReceived: Long,
      amountSent: Long
  )

  implicit val decoderXpub: Decoder[Xpub] = deriveConfiguredDecoder[Xpub]

  implicit val decoderRegisterRequest: Decoder[RegisterRequest] =
    deriveConfiguredDecoder[RegisterRequest]

  implicit val decoderSyncResult: Decoder[SyncResult] = deriveConfiguredDecoder[SyncResult]

  implicit val decoderTestCase: Decoder[TestCase] = deriveConfiguredDecoder[TestCase]
}
