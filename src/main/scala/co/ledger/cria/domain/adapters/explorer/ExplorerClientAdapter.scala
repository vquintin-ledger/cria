package co.ledger.cria.domain.adapters.explorer

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.clients.explorer
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.interpreter.{
  BlockHash,
  BlockView,
  Coin,
  Confirmation,
  TransactionView
}
import co.ledger.cria.domain.services.ExplorerClient
import co.ledger.cria.logging.CriaLogContext
import shapeless.tag
import shapeless.tag.@@
import cats.implicits._

final class ExplorerClientAdapter(client: explorer.ExplorerClient) extends ExplorerClient {
  override def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView] =
    client.getCurrentBlock.flatMap(TypeHelper.block.fromExplorer[IO])

  override def getBlock(
      hash: BlockHash
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]] =
    client.getBlock(hash.asString).flatMap(_.traverse(TypeHelper.block.fromExplorer[IO]))

  override def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[BlockHash])(
      implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Confirmed] =
    client
      .getConfirmedTransactions(addresses, blockHash.map(_.asString))
      .evalMap(TypeHelper.transaction.fromExplorer[IO])
      .map(tag[Confirmation.Confirmed].apply)

  override def getUnconfirmedTransactions(addresses: Set[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Unconfirmed] =
    client
      .getUnconfirmedTransactions(addresses)
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
  def explorerForCoin(f: explorer.models.Coin => explorer.ExplorerClient)(c: Coin): ExplorerClient =
    new ExplorerClientAdapter(f(TypeHelper.coin.toExplorer(c)))
}
