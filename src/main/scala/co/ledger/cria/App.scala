package co.ledger.cria

import cats.effect.{ExitCode, IO, IOApp, Resource}
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.ResourceUtils.grpcManagedChannel
import io.grpc.{ManagedChannel, Server}
import org.http4s.client.Client
import pureconfig.ConfigSource
import cats.implicits._
import co.ledger.cria.cli.CommandLineOptions
import co.ledger.cria.config.Config
import co.ledger.cria.clients.explorer.ExplorerHttpClient
import co.ledger.cria.clients.explorer.types.Coin
import co.ledger.cria.clients.protocol.http.Clients
import co.ledger.cria.domain.adapters.explorer.ExplorerClientAdapter
import co.ledger.cria.domain.adapters.keychain.KeychainGrpcClient
import co.ledger.cria.domain.services.{CursorStateService, ExplorerClient, HealthService}
import co.ledger.cria.domain.services.interpreter.InterpreterImpl
import co.ledger.cria.utils.{DbUtils, ResourceUtils}
import doobie.util.transactor.Transactor

object App extends IOApp with DefaultContextLogging {

  case class ClientResources(
      httpClient: Client[IO],
      keychainGrpcChannel: ManagedChannel,
      transactor: Transactor[IO]
  )

  case class WorkerResources(
      args: CommandLineOptions,
      clients: ClientResources,
      server: Server
  )

  def run(args: List[String]): IO[ExitCode] =
    run(args, ConfigSource.default.loadOrThrow[Config])

  def run(args: List[String], conf: Config): IO[ExitCode] = {

    val resources = for {
      args    <- parseCommandLine(args)
      clients <- makeClientResources(conf)
      serviceDefinitions = List(new HealthService().definition)
      grcpService <- ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions)
    } yield WorkerResources(
      args,
      clients,
      grcpService
    )

    resources
      .use { resources =>
        val clientResources = resources.clients
        val keychainClient  = new KeychainGrpcClient(clientResources.keychainGrpcChannel)
        def explorerClient(c: Coin): ExplorerClient = new ExplorerClientAdapter(
          new ExplorerHttpClient(clientResources.httpClient, conf.explorer, c)
        )
        val interpreterClient = new InterpreterImpl(
          explorerClient,
          clientResources.transactor,
          conf.maxConcurrent,
          conf.db.batchConcurrency
        )

        val cursorStateService: Coin => CursorStateService[IO] =
          c => CursorStateService(explorerClient(c), interpreterClient).getLastValidState(_, _, _)

        val cliOptions = resources.args

        val syncParams = SynchronizationParameters(
          cliOptions.keychainId,
          cliOptions.coin,
          cliOptions.syncId,
          cliOptions.blockHash,
          cliOptions.walletUid
        )

        val worker = new Synchronizer(
          keychainClient,
          explorerClient,
          interpreterClient,
          cursorStateService
        )

        for {
          _          <- DbUtils.flywayMigrate(conf.db.postgres)
          _          <- IO(resources.server.start()) *> log.info("Worker started")
          syncResult <- worker.run(syncParams)
          _          <- IO(resources.server.shutdown()) *> log.info("Worker stopped")
        } yield syncResult
      }
      .attempt
      .flatMap {
        case Right(_: SynchronizationResult.SynchronizationSuccess) => IO.pure(ExitCode.Success)
        case Right(_: SynchronizationResult.SynchronizationFailure) => IO.pure(ExitCode.Error)
        case Left(t)                                                => log.error("Got non caught error", t).as(ExitCode.Error)
      }
  }

  private def parseCommandLine(args: List[String]): Resource[IO, CommandLineOptions] = {
    val lift = Resource.liftK[IO]
    lift(
      IO.fromEither(
        CommandLineOptions.command
          .parse(args)
          .leftMap(help => new IllegalArgumentException(help.toString()))
      )
    )
  }

  def makeClientResources(conf: Config): Resource[IO, ClientResources] =
    for {
      httpClient          <- Clients.htt4s
      keychainGrpcChannel <- grpcManagedChannel(conf.keychain)
      transactor          <- ResourceUtils.postgresTransactor(conf.db.postgres)
    } yield ClientResources(httpClient, keychainGrpcChannel, transactor)
}
