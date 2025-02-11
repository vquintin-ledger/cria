package co.ledger.cria

import cats.effect.{ExitCode, IO, IOApp, Resource}
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.ResourceUtils.grpcManagedChannel
import io.grpc.Server
import pureconfig.ConfigSource
import cats.implicits._
import co.ledger.cria.cli.CommandLineOptions
import co.ledger.cria.config.{Config, ExplorerConfig, GrpcClientConfig, PersistenceConfig}
import co.ledger.cria.domain.CriaModule
import co.ledger.cria.domain.adapters.explorer.v2
import co.ledger.cria.domain.adapters.explorer.v3
import co.ledger.cria.domain.adapters.keychain.KeychainGrpcClient
import co.ledger.cria.domain.adapters.persistence.lama
import co.ledger.cria.domain.adapters.persistence.wd
import co.ledger.cria.domain.adapters.persistence.tee
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.models.{SynchronizationParameters, SynchronizationResult}
import co.ledger.cria.domain.services.{ExplorerClient, HealthService, KeychainClient}
import co.ledger.cria.domain.services.interpreter.PersistenceFacade
import co.ledger.cria.utils.ResourceUtils

object App extends IOApp with DefaultContextLogging {

  case class ClientResources(
      explorerClient: Coin => ExplorerClient,
      keychainClient: KeychainClient,
      persistenceFacade: PersistenceFacade
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

        val cliOptions = resources.args

        val syncParams = SynchronizationParameters(
          cliOptions.keychainId,
          cliOptions.coin,
          cliOptions.syncId,
          cliOptions.blockHash,
          cliOptions.accountUid,
          cliOptions.walletUid
        )

        val criaModule =
          new CriaModule(
            clientResources.persistenceFacade,
            clientResources.keychainClient,
            clientResources.explorerClient
          )

        for {
          _          <- IO(resources.server.start()) *> log.info("Worker started")
          syncResult <- criaModule.synchronizer.run(syncParams)
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
      explorerClient    <- makeExplorerClient(conf.explorer)
      keychainClient    <- makeKeychainClient(conf.keychain)
      persistenceFacade <- makePersistenceFacade(conf.persistence)
    } yield ClientResources(explorerClient, keychainClient, persistenceFacade)

  def makeExplorerClient(conf: ExplorerConfig): Resource[IO, Coin => ExplorerClient] =
    ExplorerConfig.foldM[Resource[IO, *], Coin => ExplorerClient](
      v2.ExplorerClientAdapter.apply,
      v3.ExplorerClientAdapter.apply
    )(conf)

  def makeKeychainClient(config: GrpcClientConfig): Resource[IO, KeychainClient] =
    grpcManagedChannel(config).map(new KeychainGrpcClient(_))

  def makePersistenceFacade(config: PersistenceConfig): Resource[IO, PersistenceFacade] =
    PersistenceConfig.foldM[Resource[IO, *], PersistenceFacade](
      wd.WDPersistenceFacade.apply,
      lama.LamaPersistenceFacade.apply,
      (f1, f2, conf) =>
        Resource.pure[IO, PersistenceFacade](tee.PersistenceFacadeTee(f1, f2, conf, log))
    )(config)
}
