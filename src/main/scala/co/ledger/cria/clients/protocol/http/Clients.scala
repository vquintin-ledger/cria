package co.ledger.cria.clients.protocol.http

import cats.effect.{ConcurrentEffect, IO, Resource}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object Clients {

  def htt4s(implicit ce: ConcurrentEffect[IO]): Resource[IO, Client[IO]] =
    BlazeClientBuilder[IO](
      ExecutionContext.fromExecutorService(Executors.newCachedThreadPool)
    ).resource

}
