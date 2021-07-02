package co.ledger.cria.domain.adapters.explorer

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.clients.explorer
import co.ledger.cria.domain.models.interpreter.{BlockView, Coin, Confirmation, TransactionView}
import co.ledger.cria.domain.services.ExplorerClient
import co.ledger.cria.logging.CriaLogContext
import shapeless.tag
import shapeless.tag.@@

final class ExplorerClientAdapter(client: explorer.ExplorerClient) extends ExplorerClient {
  override def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView] =
    client.getCurrentBlock.map(TypeHelper.block.fromExplorer)

  override def getBlock(
      hash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]] =
    client.getBlock(hash).map(_.map(TypeHelper.block.fromExplorer))

  override def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Confirmed] =
    client
      .getConfirmedTransactions(addresses, blockHash)
      .map(TypeHelper.transaction.fromExplorer _ andThen tag[Confirmation.Confirmed].apply)

  override def getUnconfirmedTransactions(addresses: Set[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Unconfirmed] =
    client
      .getUnconfirmedTransactions(addresses)
      .map(TypeHelper.transaction.fromExplorer _ andThen tag[Confirmation.Unconfirmed].apply)

  override def getTransaction(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]] =
    client.getTransaction(transactionHash).map(_.map(TypeHelper.transaction.fromExplorer))
}

object ExplorerClientAdapter {
  def explorerForCoin(f: explorer.models.Coin => explorer.ExplorerClient)(c: Coin): ExplorerClient =
    new ExplorerClientAdapter(f(TypeHelper.coin.toExplorer(c)))
}
