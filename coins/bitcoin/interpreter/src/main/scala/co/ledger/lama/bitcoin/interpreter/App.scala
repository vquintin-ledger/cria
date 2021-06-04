package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerHttpClient
import co.ledger.lama.common.services.Clients
import co.ledger.lama.common.services.grpc.HealthService
import co.ledger.lama.common.utils.ResourceUtils.{grpcServer, postgresTransactor}
import co.ledger.lama.common.utils.DbUtils
import fs2.Stream
import pureconfig.ConfigSource

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {

      httpClient <- Clients.htt4s
      explorerService = new ExplorerHttpClient(httpClient, conf.explorer, _)

      // create the db transactor
      db <- postgresTransactor(conf.db.postgres)

      // define rpc service definitions
      serviceDefinitions = List(
        new InterpreterGrpcService(
          new Interpreter(
            explorerService,
            db,
            conf.maxConcurrent,
            conf.db.batchConcurrency
          )
        ).definition,
        new HealthService().definition
      )

      // create the grpc server
      grpcServer <- grpcServer(conf.grpcServer, serviceDefinitions)
    } yield grpcServer

    Stream
      .resource(resources)
      .evalMap { server =>
        // migrate db then start server
        DbUtils.flywayMigrate(conf.db.postgres) *> IO(server.start())
      }
      .evalMap(_ => IO.never)
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
