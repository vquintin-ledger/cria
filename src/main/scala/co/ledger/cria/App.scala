package co.ledger.cria

import cats.effect.{ExitCode, IO, IOApp, Resource}
import co.ledger.cria.clients.grpc.KeychainGrpcClient
import co.ledger.cria.clients.http.ExplorerHttpClient
import co.ledger.cria.services.{CursorStateService, HealthService}
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.ResourceUtils.grpcManagedChannel
import io.grpc.{ManagedChannel, Server}
import org.http4s.client.Client
import pureconfig.ConfigSource
import cats.implicits._
import co.ledger.cria.cli.CommandLineOptions
import co.ledger.cria.config.Config
import co.ledger.cria.clients.Clients
import co.ledger.cria.models.account.Coin
import co.ledger.cria.services.interpreter.InterpreterImpl
import co.ledger.cria.utils.{DbUtils, ResourceUtils}
import doobie.util.transactor.Transactor

object App extends IOApp with DefaultContextLogging {

  case class WorkerResources(
      args: CommandLineOptions,
      httpClient: Client[IO],
      keychainGrpcChannel: ManagedChannel,
      server: Server,
      transactor: Transactor[IO]
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      args                <- parseCommandLine(args)
      httpClient          <- Clients.htt4s
      keychainGrpcChannel <- grpcManagedChannel(conf.keychain)

      serviceDefinitions = List(new HealthService().definition)

      grcpService <- ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions)
      transactor  <- ResourceUtils.postgresTransactor(conf.db.postgres)
    } yield WorkerResources(
      args,
      httpClient,
      keychainGrpcChannel,
      grcpService,
      transactor
    )

    resources
      .use { res =>
        val keychainClient = new KeychainGrpcClient(res.keychainGrpcChannel)
        val explorerClient = new ExplorerHttpClient(res.httpClient, conf.explorer, _)
        val interpreterClient = new InterpreterImpl(
          explorerClient,
          res.transactor,
          conf.maxConcurrent,
          conf.db.batchConcurrency
        )

        val cursorStateService: Coin => CursorStateService[IO] =
          c => CursorStateService(explorerClient(c), interpreterClient).getLastValidState(_, _, _)

        val cliOptions = res.args

        val syncParams = SynchronizationParameters(
          cliOptions.xpub,
          cliOptions.scheme,
          cliOptions.coin,
          cliOptions.syncId,
          cliOptions.blockHash,
          cliOptions.walletUid,
          cliOptions.lookahead
        )

        val worker = new Synchronizer(
          keychainClient,
          explorerClient,
          interpreterClient,
          cursorStateService
        )

        for {
          _   <- DbUtils.flywayMigrate(conf.db.postgres)
          _   <- IO(res.server.start()) *> log.info("Worker started")
          res <- worker.run(syncParams)
        } yield res
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
}
