package co.ledger.cria.domain.mocks

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.domain.models.interpreter.{BlockView, Confirmation, TransactionView}
import co.ledger.cria.domain.services.ExplorerClient
import co.ledger.cria.logging.CriaLogContext
import fs2.Stream
import shapeless.tag.@@

import scala.collection.mutable

class ExplorerClientMock(
    blockchain: Map[String, List[TransactionView @@ Confirmation.Confirmed]] = Map.empty,
    mempool: Map[String, List[TransactionView @@ Confirmation.Unconfirmed]] = Map.empty
) extends ExplorerClient {

  val blocks: List[BlockView] = blockchain.values.flatten.flatten(_.block).toList.sorted

  var getConfirmedTransactionsCount: Int   = 0
  var getUnConfirmedTransactionsCount: Int = 0

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView] =
    IO.pure(blockchain.values.flatten.flatMap(_.block).max)

  def getBlock(hash: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]] =
    IO.pure(blocks.find(_.hash == hash))

  def getUnconfirmedTransactions(
      addresses: Set[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, TransactionView @@ Confirmation.Unconfirmed] = {
    getUnConfirmedTransactionsCount += 1
    Stream.emits(addresses.flatMap(mempool.get).flatten.toSeq)
  }

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): fs2.Stream[IO, TransactionView @@ Confirmation.Confirmed] = {
    getConfirmedTransactionsCount += 1
    Stream.emits(addresses.flatMap(blockchain.get).flatten)
  }

  val txs: mutable.Map[String, TransactionView] = mutable.Map()
  def addToBC(tx: TransactionView): Unit        = txs.update(tx.hash, tx)
  def removeFromBC(hash: String): Unit          = txs.remove(hash)

  def getTransaction(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]] = {
    IO.pure(txs.get(transactionHash))
  }
}
