package co.ledger.cria.domain.services

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.domain.models.interpreter.{
  BlockView,
  ConfirmedTransactionView,
  TransactionView,
  UnconfirmedTransactionView
}
import co.ledger.cria.logging.CriaLogContext
import fs2.Stream

trait ExplorerClient {

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView]

  def getBlock(hash: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]]

  def getConfirmedTransactions(
      addresses: Seq[String],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, ConfirmedTransactionView]

  def getUnconfirmedTransactions(
      addresses: Set[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, UnconfirmedTransactionView]

  def getTransaction(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]]
}
