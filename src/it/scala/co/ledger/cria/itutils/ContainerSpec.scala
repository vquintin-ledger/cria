package co.ledger.cria.itutils

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits.catsSyntaxFlatMapOps
import co.ledger.cria.App
import co.ledger.cria.App.ClientResources
import co.ledger.cria.clients.protocol.grpc.GrpcClient
import co.ledger.cria.config.{Config, GrpcClientConfig, PersistenceConfig}
import co.ledger.cria.domain.adapters.persistence.lama.LamaDb
import co.ledger.cria.domain.adapters.persistence.tee.TeeConfig
import co.ledger.cria.domain.adapters.persistence.wd.WalletDaemonDb
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.services.KeychainClient
import co.ledger.cria.domain.services.interpreter.{Interpreter, InterpreterImpl}
import co.ledger.cria.e2e.RegisterRequest
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.ResourceUtils.grpcManagedChannel
import co.ledger.protobuf.bitcoin.keychain
import co.ledger.protobuf.bitcoin.keychain.KeychainServiceFs2Grpc
import com.dimafeng.testcontainers.{
  DockerComposeContainer,
  ExposedService,
  ForAllTestContainer,
  ServiceLogConsumer
}
import io.grpc.Metadata
import org.scalatest.Suite
import pureconfig.ConfigSource

import java.io.File
import scala.concurrent.ExecutionContext

trait ContainerSpec extends ForAllTestContainer with DefaultContextLogging { s: Suite =>

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val postgresPort = 5432

  val keychainPort = 50052

  import org.testcontainers.containers.output.Slf4jLogConsumer

  private def makeLogConsumer(prefix: String) =
    new Slf4jLogConsumer(log.logger.underlying).withPrefix(prefix)

  lazy val container: DockerComposeContainer =
    new DockerComposeContainer(
      new File("docker-compose-it.yml"),
      exposedServices = List(
        ExposedService("postgres_1", postgresPort),
        ExposedService("bitcoin-keychain_1", keychainPort)
      ),
      logConsumers = List(
        ServiceLogConsumer("bitcoin-keychain_1", makeLogConsumer("bitcoin-keychain")),
        ServiceLogConsumer("redis_1", makeLogConsumer("redis"))
      )
    )

  def setupDB: IO[Unit] =
    testResources.use { r =>
      val utils = r.testUtils
      utils.clean >> utils.migrate
    }

  def setupAccount(request: RegisterRequest): IO[Int] = {
    testResources.use { tr =>
      val utils = tr.testUtils
      utils.setupAccount(AccountUid(request.accountUid), WalletUid(request.walletUid))
    }
  }

  def appResources: Resource[IO, ClientResources] =
    App.makeClientResources(conf)

  /*
   * Uses the dockerized keychain and production explorer
   */
  def testResources: Resource[IO, TestResources] =
    for {
      resources <- appResources
      testUtils <- TestUtils.fromConfig(conf.persistence, log)
      interpreterClient = new InterpreterImpl(
        resources.explorerClient,
        resources.persistenceFacade
      )
      keychainGrpcChannel <- grpcManagedChannel(conf.keychain)
    } yield TestResources(
      resources,
      interpreterClient,
      resources.keychainClient,
      GrpcClient.resolveClient(
        keychain.KeychainServiceFs2Grpc.stub[IO],
        keychainGrpcChannel,
        "keychainClient"
      ),
      testUtils
    )

  case class TestResources(
      clients: ClientResources,
      interpreter: Interpreter,
      keychainClient: KeychainClient,
      rawKeychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      testUtils: TestUtils
  )

  lazy val conf: Config = {
    val defaultConf = ConfigSource.default.loadOrThrow[Config]
    defaultConf.copy(
      keychain = new GrpcClientConfig(
        container.getServiceHost("bitcoin-keychain_1", keychainPort),
        container.getServicePort("bitcoin-keychain_1", keychainPort),
        false
      ),
      persistence = adaptedPersistenceConfig(defaultConf.persistence)
    )
  }

  private def adaptedPersistenceConfig(conf: PersistenceConfig): PersistenceConfig = {
    def wd(db: WalletDaemonDb): PersistenceConfig.WalletDaemon = {
      val mappedPostgresHost = container.getServiceHost("postgres_1", postgresPort)
      val mappedPostgresPort = container.getServicePort("postgres_1", postgresPort)
      PersistenceConfig.WalletDaemon(
        db.copy(
          walletDaemon = db.walletDaemon
            .copy(url = s"jdbc:postgresql://$mappedPostgresHost:$mappedPostgresPort/wd_local_pool"),
          criaExtra = db.walletDaemon.copy(url =
            s"jdbc:postgresql://$mappedPostgresHost:$mappedPostgresPort/wd_cria_extra"
          )
        )
      )
    }

    def lama(db: LamaDb): PersistenceConfig.Lama = {
      val mappedPostgresHost = container.getServiceHost("postgres_1", postgresPort)
      val mappedPostgresPort = container.getServicePort("postgres_1", postgresPort)
      PersistenceConfig.Lama(
        db.copy(postgres =
          db.postgres.copy(url =
            s"jdbc:postgresql://$mappedPostgresHost:$mappedPostgresPort/test_lama_btc"
          )
        )
      )
    }

    def both(left: PersistenceConfig, right: PersistenceConfig, tee: TeeConfig) =
      PersistenceConfig.Tee(left, right, tee)

    PersistenceConfig.fold(wd, lama, both)(conf)
  }
}
