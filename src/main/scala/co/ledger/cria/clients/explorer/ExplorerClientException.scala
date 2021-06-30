package co.ledger.cria.clients.explorer

import org.http4s.Uri

case class ExplorerClientException(uri: Uri, t: Throwable)
    extends Exception(s"Error on explorer - ${uri.renderString}", t)
