package co.ledger.cria.clients.explorer.mocks

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.clients.explorer.ExplorerClient
import co.ledger.cria.clients.explorer.ExplorerClient.Address
import co.ledger.cria.clients.explorer.types.{
  Block,
  ConfirmedTransaction,
  Transaction,
  UnconfirmedTransaction
}
import co.ledger.cria.logging.CriaLogContext
import fs2.Stream

import scala.collection.mutable

class ExplorerClientMock(
    blockchain: Map[Address, List[ConfirmedTransaction]] = Map.empty,
    mempool: Map[Address, List[UnconfirmedTransaction]] = Map.empty
) extends ExplorerClient {

  val blocks = blockchain.values.flatten.map(_.block).toList.sorted

  var getConfirmedTransactionsCount: Int   = 0
  var getUnConfirmedTransactionsCount: Int = 0

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block] =
    IO.pure(blockchain.values.flatten.map(_.block).max)

  def getBlock(hash: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Block]] =
    IO.pure(blocks.find(_.hash == hash))

  def getBlock(height: Long)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block] =
    IO.pure(blocks.find(_.height == height).get)

  def getUnconfirmedTransactions(
      addresses: Set[Address]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, UnconfirmedTransaction] = {
    getUnConfirmedTransactionsCount += 1
    Stream.emits(addresses.flatMap(mempool.get).flatten.toSeq)
  }

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, ConfirmedTransaction] = {
    getConfirmedTransactionsCount += 1
    Stream.emits(addresses.flatMap(blockchain.get).flatten)
  }

  override def broadcastTransaction(
      tx: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[String] = ???

  override def getRawTransactionHex(transactionHash: String)(implicit
      lc: CriaLogContext,
      t: Timer[IO]
  ): IO[String] =
    IO.pure("raw hex for " ++ transactionHash)

  val txs: mutable.Map[String, Transaction] = mutable.Map()
  def addToBC(tx: Transaction): Unit        = txs.update(tx.hash, tx)
  def removeFromBC(hash: String): Unit      = txs.remove(hash)

  def getTransaction(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Transaction]] = {
    IO.pure(txs.get(transactionHash))
  }
}
