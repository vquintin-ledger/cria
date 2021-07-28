package co.ledger.cria.clients.explorer

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.clients.explorer.ExplorerClient.Address
import co.ledger.cria.clients.explorer.models.{
  Block,
  ConfirmedTransaction,
  Transaction,
  UnconfirmedTransaction
}
import co.ledger.cria.logging.CriaLogContext
import fs2.Stream

trait ExplorerClient {

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block]

  def getBlock(hash: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Block]]

  def getBlock(height: Long)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block]

  def getConfirmedTransactions(
      addresses: Seq[Address],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, ConfirmedTransaction]

  def getUnconfirmedTransactions(
      addresses: Set[Address]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, UnconfirmedTransaction]

  def broadcastTransaction(tx: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[String]

  def getRawTransactionHex(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[String]

  def getTransaction(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Transaction]]
}

object ExplorerClient {
  type Address = String
}
