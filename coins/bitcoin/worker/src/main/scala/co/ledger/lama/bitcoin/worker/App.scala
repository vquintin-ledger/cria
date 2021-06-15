package co.ledger.lama.bitcoin.worker

import cats.effect.{ExitCode, IO, IOApp, Resource}
import co.ledger.lama.bitcoin.common.clients.grpc.{InterpreterGrpcClient, KeychainGrpcClient}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerHttpClient
import co.ledger.lama.bitcoin.worker.cli.CommandLineOptions
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.logging.DefaultContextLogging
import co.ledger.lama.common.services.Clients
import co.ledger.lama.common.services.grpc.HealthService
import co.ledger.lama.common.models.Coin
import co.ledger.lama.common.utils.ResourceUtils
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import io.grpc.{ManagedChannel, Server}
import org.http4s.client.Client
import pureconfig.ConfigSource
import cats.implicits._

object App extends IOApp with DefaultContextLogging {

  case class WorkerResources(
      args: CommandLineOptions,
      httpClient: Client[IO],
      keychainGrpcChannel: ManagedChannel,
      interpreterGrpcChannel: ManagedChannel,
      server: Server
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      args                   <- parseCommandLine(args)
      httpClient             <- Clients.htt4s
      keychainGrpcChannel    <- grpcManagedChannel(conf.keychain)
      interpreterGrpcChannel <- grpcManagedChannel(conf.interpreter)

      serviceDefinitions = List(new HealthService().definition)

      grcpService <- ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions)

    } yield WorkerResources(
      args,
      httpClient,
      keychainGrpcChannel,
      interpreterGrpcChannel,
      grcpService
    )

    resources.use { res =>
      val keychainClient    = new KeychainGrpcClient(res.keychainGrpcChannel)
      val interpreterClient = new InterpreterGrpcClient(res.interpreterGrpcChannel)
      val explorerClient    = new ExplorerHttpClient(res.httpClient, conf.explorer, _)

      val cursorStateService: Coin => CursorStateService[IO] =
        c => CursorStateService(explorerClient(c), interpreterClient).getLastValidState(_, _, _)

      val cliOptions = res.args

      val args = SynchronizationParameters(
        cliOptions.xpub,
        cliOptions.scheme,
        cliOptions.coin,
        cliOptions.syncId,
        cliOptions.cursor,
        cliOptions.walletId,
        cliOptions.lookahead
      )

      val worker = new Worker(
        args,
        keychainClient,
        explorerClient,
        interpreterClient,
        cursorStateService
      )
      for {
        _ <- IO(res.server.start()) *> log.info("Worker started")

        res <- worker.run.as(ExitCode.Success)
      } yield res
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
