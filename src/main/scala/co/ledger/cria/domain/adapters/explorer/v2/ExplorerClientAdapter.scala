package co.ledger.cria.domain.adapters.explorer.v2

import cats.effect.{ContextShift, IO, Resource, Timer}
import cats.implicits._
import co.ledger.cria.clients.explorer.v2.ExplorerHttpClient
import co.ledger.cria.clients.explorer.v2.models.ExplorerConfig
import co.ledger.cria.clients.explorer.{v2 => explorer}
import co.ledger.cria.clients.protocol.http.Clients
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.services.explorer.ExplorerClient
import co.ledger.cria.logging.CriaLogContext
import shapeless.tag
import shapeless.tag.@@

final class ExplorerClientAdapter(client: explorer.ExplorerClient, fallback: ExplorerClient)
    extends ExplorerClient {
  override def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView] =
    client.getCurrentBlock.flatMap(TypeHelper.block.fromExplorer[IO])

  override def getBlock(
      hash: BlockHash
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]] = fallback.getBlock(hash)

  override def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[BlockHash])(
      implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Confirmed] =
    client
      .getTransactions(addresses, blockHash.map(_.asString))
      .filter(t => t.block.isDefined)
      .evalMap(TypeHelper.transaction.fromExplorer[IO])
      .map(tag[Confirmation.Confirmed].apply)

  override def getUnconfirmedTransactions(addresses: Set[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Unconfirmed] =
    client
      .getTransactions(addresses.toSeq, None)
      .filter(t => t.block.isEmpty)
      .evalMap(TypeHelper.transaction.fromExplorer[IO])
      .map(tag[Confirmation.Unconfirmed].apply)

  override def getTransaction(
      transactionHash: TxHash
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]] =
    client
      .getTransaction(transactionHash.asString)
      .flatMap(_.traverse(TypeHelper.transaction.fromExplorer[IO]))
}

object ExplorerClientAdapter {
  def apply(
      config: ExplorerConfig,
      fallback: Coin => ExplorerClient
  )(implicit cs: ContextShift[IO]): Resource[IO, Coin => ExplorerClient] =
    Clients.htt4s.map(c =>
      ExplorerClientAdapter.explorerForCoin(new ExplorerHttpClient(c, config, _), fallback) _
    )

  private def explorerForCoin(
      f: explorer.models.Coin => explorer.ExplorerClient,
      fallback: Coin => ExplorerClient
  )(
      c: Coin
  ): ExplorerClient =
    new ExplorerClientAdapter(f(TypeHelper.coin.toExplorer(c)), fallback(c))
}
