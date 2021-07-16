package co.ledger.cria.itutils

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.App
import co.ledger.cria.App.ClientResources
import co.ledger.cria.clients.explorer.ExplorerHttpClient
import co.ledger.cria.clients.protocol.grpc.GrpcClient
import co.ledger.cria.config.{Config, GrpcClientConfig}
import co.ledger.cria.domain.adapters.explorer.ExplorerClientAdapter
import co.ledger.cria.domain.adapters.keychain.KeychainGrpcClient
import co.ledger.cria.domain.adapters.wd.{FlaggingServiceImpl, OperationServiceImpl, TransactionServiceImpl, WDServiceImpl}
import co.ledger.cria.domain.services.KeychainClient
import co.ledger.cria.domain.services.interpreter.{FlaggingService, Interpreter, InterpreterImpl, OperationService, TransactionService, WDService}
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.DbUtils
import co.ledger.protobuf.bitcoin.keychain
import co.ledger.protobuf.bitcoin.keychain.KeychainServiceFs2Grpc
import com.dimafeng.testcontainers.{DockerComposeContainer, ExposedService, ForAllTestContainer, ServiceLogConsumer}
import io.grpc.Metadata
import org.flywaydb.core.Flyway
import org.scalatest.flatspec.AnyFlatSpec
import pureconfig.ConfigSource

import java.io.File
import scala.concurrent.ExecutionContext

trait ContainerFlatSpec extends AnyFlatSpec with ForAllTestContainer with DefaultContextLogging {

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

  def setup: IO[Unit] = {
    lazy val flyway: Flyway = DbUtils.flyway(conf.db.postgres)
    IO(flyway.clean()) *> IO(flyway.migrate())
  }

  def appResources: Resource[IO, ClientResources] =
    App.makeClientResources(conf)

  /*
   * Uses the dockerized keychain and production explorer
   */
  def testResources: Resource[IO, TestResources] =
    appResources.map { resources =>
      val explorerClient = {
        ExplorerClientAdapter.explorerForCoin(
          new ExplorerHttpClient(resources.httpClient, conf.explorer, _)
        ) _
      }
      val flaggingService = new FlaggingServiceImpl(resources.transactor)
      val transactionService = new TransactionServiceImpl(resources.transactor, conf.maxConcurrent)
      val operationService = new OperationServiceImpl(resources.transactor)
      val wdService = new WDServiceImpl(resources.transactor)
      val interpreterClient = new InterpreterImpl(
        explorerClient,
        resources.transactor,
        flaggingService,
        transactionService,
        operationService,
        wdService
      )
      val keychainClient = new KeychainGrpcClient(resources.keychainGrpcChannel)
      TestResources(
        resources,
        interpreterClient,
        keychainClient,
        GrpcClient.resolveClient(
          keychain.KeychainServiceFs2Grpc.stub[IO],
          resources.keychainGrpcChannel,
          "keychainClient"
        ),
        new TestUtils(resources.transactor),
        flaggingService,
        transactionService,
        operationService,
        wdService
      )
    }

  case class TestResources(
      clients: ClientResources,
      interpreter: Interpreter,
      keychainClient: KeychainClient,
      rawKeychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      testUtils: TestUtils,
      flaggingService: FlaggingService,
      transactionService: TransactionService,
      operationService: OperationService,
      wdService: WDService
  )

  lazy val conf: Config = {
    val defaultConf        = ConfigSource.default.loadOrThrow[Config]
    val mappedPostgresHost = container.getServiceHost("postgres_1", postgresPort)
    val mappedPostgresPort = container.getServicePort("postgres_1", postgresPort)
    defaultConf.copy(
      keychain = new GrpcClientConfig(
        container.getServiceHost("bitcoin-keychain_1", keychainPort),
        container.getServicePort("bitcoin-keychain_1", keychainPort),
        false
      ),
      db = defaultConf.db.copy(postgres =
        defaultConf.db.postgres
          .copy(url = s"jdbc:postgresql://$mappedPostgresHost:$mappedPostgresPort/test_lama_btc")
      )
    )
  }
}
