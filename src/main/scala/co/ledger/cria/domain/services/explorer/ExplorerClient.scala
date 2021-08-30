package co.ledger.cria.domain.services.explorer

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.interpreter.{
  BlockHash,
  BlockView,
  Confirmation,
  TransactionView
}
import co.ledger.cria.logging.CriaLogContext
import fs2.Stream
import shapeless.tag.@@

trait ExplorerClient {

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView]

  def getBlock(hash: BlockHash)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]]

  def getConfirmedTransactions(
      addresses: Seq[String],
      blockHash: Option[BlockHash]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, TransactionView @@ Confirmation.Confirmed]

  def getUnconfirmedTransactions(
      addresses: Set[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, TransactionView @@ Confirmation.Unconfirmed]

  def getTransaction(
      transactionHash: TxHash
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]]
}
