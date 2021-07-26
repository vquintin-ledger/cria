package co.ledger.cria.e2e.recovery

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits.catsSyntaxFlatMapOps
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.interpreter.{
  BlockHash,
  BlockView,
  Confirmation,
  TransactionView
}
import co.ledger.cria.domain.services.ExplorerClient
import co.ledger.cria.logging.CriaLogContext
import shapeless.tag.@@

final class ExplorerClientRunBefore(explorerClient: ExplorerClient, action: IO[Unit])
    extends ExplorerClient {
  override def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView] =
    action >> explorerClient.getCurrentBlock

  override def getBlock(
      hash: BlockHash
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]] =
    action >> explorerClient.getBlock(hash)

  override def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[BlockHash])(
      implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Confirmed] =
    fs2.Stream.eval(action) >> explorerClient.getConfirmedTransactions(addresses, blockHash)

  override def getUnconfirmedTransactions(addresses: Set[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Unconfirmed] =
    fs2.Stream.eval(action) >> explorerClient.getUnconfirmedTransactions(addresses)

  override def getTransaction(
      transactionHash: TxHash
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]] =
    action >> explorerClient.getTransaction(transactionHash)
}
