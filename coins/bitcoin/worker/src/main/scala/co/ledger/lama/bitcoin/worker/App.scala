package co.ledger.lama.bitcoin.worker

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.common.clients.grpc.{InterpreterGrpcClient, KeychainGrpcClient}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerHttpClient
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

object App extends IOApp with DefaultContextLogging {

  case class WorkerResources(
      httpClient: Client[IO],
      keychainGrpcChannel: ManagedChannel,
      interpreterGrpcChannel: ManagedChannel,
      server: Server
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      httpClient             <- Clients.htt4s
      keychainGrpcChannel    <- grpcManagedChannel(conf.keychain)
      interpreterGrpcChannel <- grpcManagedChannel(conf.interpreter)

      serviceDefinitions = List(new HealthService().definition)

      grcpService <- ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions)

    } yield WorkerResources(
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

      val worker = new Worker(
        ???,
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

}
