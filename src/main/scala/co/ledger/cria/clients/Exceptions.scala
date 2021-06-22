package co.ledger.cria.clients

import org.http4s.Uri

object Exceptions {
  case class ExplorerClientException(uri: Uri, t: Throwable)
      extends Exception(s"Error on explorer - ${uri.renderString}", t)

  case class GrpcClientException(t: Throwable, clientName: String)
      extends Exception(s"$clientName - ${t.getMessage}", t)

  case object MalformedProtobufUuidException extends Exception("Invalid UUID on protobuf request")

}
