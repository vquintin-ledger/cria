package co.ledger.cria.clients.grpc

import co.ledger.cria.clients.Exceptions.GrpcClientException
import co.ledger.cria.logging.DefaultContextLogging
import io.grpc.{CallOptions, ManagedChannel, StatusRuntimeException}

object GrpcClient extends DefaultContextLogging {
  type Builder[Client] =
    (ManagedChannel, CallOptions, StatusRuntimeException => Option[GrpcClientException]) => Client

  private def onError(
      clientName: String
  )(e: StatusRuntimeException): Option[GrpcClientException] =
    Some(GrpcClientException(e, clientName))

  def resolveClient[Client](
      f: Builder[Client],
      managedChannel: ManagedChannel,
      clientName: String
  ): Client = f(managedChannel, CallOptions.DEFAULT, onError(clientName))
}
