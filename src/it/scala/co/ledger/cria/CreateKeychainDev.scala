package co.ledger.cria

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.App.ClientResources
import co.ledger.cria.clients.protocol.grpc.GrpcClient
import co.ledger.cria.config.Config
import co.ledger.cria.domain.adapters.keychain.KeychainGrpcClient
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.domain.services.KeychainClient
import co.ledger.cria.itutils.TestUtils
import co.ledger.cria.itutils.models.keychain.AccountKey.Xpub
import co.ledger.cria.itutils.models.keychain.{KeychainInfo, Scheme}
import co.ledger.cria.itutils.models.keychain.CoinImplicits._
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.IOAssertion
import co.ledger.protobuf.bitcoin.keychain
import co.ledger.protobuf.bitcoin.keychain.KeychainServiceFs2Grpc
import io.grpc.Metadata
import org.scalatest.flatspec.AnyFlatSpec
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

class CreateKeychainDev extends AnyFlatSpec with DefaultContextLogging {

  val conf: Config                  = ConfigSource.default.loadOrThrow[Config]
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  "keychain" should "create and print keychain" in IOAssertion {

    for {
      keychain <- makeKeychainId(
        RegisterRequest(
          Xpub(
            "xpub6C3hkmDKqMgVR5JXnmj7g3Zm5fDdyfAqciXaTK6nubYHuwPZVyfq6mVPqRfe4kisVqZibNWgz7euv5EucEgHJCt7DjnoeVUrKoaqdoNQBsc"
          ),
          Scheme.Bip44,
          20,
          Coin.Btc
        )
      )

      _ = println(keychain)

    } yield ()

  }

  case class RegisterRequest(
      accountKey: Xpub,
      scheme: Scheme,
      lookaheadSize: Int,
      coin: Coin
  )

  case class TestResources(
      clients: ClientResources,
      keychainClient: KeychainClient,
      rawKeychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      testUtils: TestUtils
  )

  def appResources: Resource[IO, ClientResources] =
    App.makeClientResources(conf)

  def testResources: Resource[IO, TestResources] = {
    for {
      resources <- appResources
      testUtils <- TestUtils.fromConfig(conf.db, log)
      keychainClient = new KeychainGrpcClient(resources.keychainGrpcChannel)
    } yield       TestResources(
      resources,
      keychainClient,
      GrpcClient.resolveClient(
        keychain.KeychainServiceFs2Grpc.stub[IO],
        resources.keychainGrpcChannel,
        "keychainClient"
      ),
      testUtils,
    )
  }

  def makeKeychainId(request: RegisterRequest): IO[KeychainId] =
    testResources.use { tr =>
      tr.rawKeychainClient
        .createKeychain(
          keychain.CreateKeychainRequest(
            request.accountKey.toProto,
            request.scheme.toProto,
            request.lookaheadSize,
            Some(request.coin.toNetwork.toKeychainChainParamsProto)
          ),
          new Metadata
        )
        .map(KeychainInfo.fromProto)
        .map(_.keychainId)
    }

}
