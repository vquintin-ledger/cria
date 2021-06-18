package co.ledger.lama.bitcoin.worker

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.clients.grpc.InterpreterClient
import co.ledger.lama.bitcoin.common.clients.grpc.mocks.InterpreterClientMock
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient
import co.ledger.lama.bitcoin.common.clients.http.mocks.ExplorerClientMock
import co.ledger.lama.bitcoin.common.models.Scheme.Bip44
import co.ledger.lama.bitcoin.common.models.explorer.Block
import co.ledger.lama.bitcoin.common.models.interpreter.TransactionView
import co.ledger.lama.bitcoin.common.models.keychain.AccountKey.Xpub
import co.ledger.lama.bitcoin.worker.services.CursorStateService
import co.ledger.lama.common.models.{Account, Coin, CoinFamily}
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

class WorkerSpec extends AnyFlatSpec with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val keychainId = UUID.randomUUID()

  val accountIdentifier =
    Account(
      keychainId.toString,
      CoinFamily.Bitcoin,
      Coin.Btc
    )
  val accountAddresses = LazyList.from(1).map(_.toString)
  val blockchain = LazyList
    .from(0)
    .map { i =>
      val block   = Block((i + 1000).toString, i, Instant.now())
      val address = i.toString
      block -> List(address -> List(TransactionFixture.confirmed.receive(address, inBlock = block)))
    }
    .take(5)
    .toList

  val mempool = LazyList
    .from(100)
    .map { i =>
      val address = i.toString
      address -> List(TransactionFixture.receive(address))
    }
    .take(1)
    .toList

  val defaultExplorer = new ExplorerClientMock(
    blockchain.flatMap(_._2).toMap
  )
  val alreadyValidBlockCursorService: CursorStateService[IO] = (_, b, _) => IO.pure(b)

  def worker(
      interpreter: InterpreterClient = new InterpreterClientMock,
      explorer: ExplorerClient = defaultExplorer
  ) = new Worker(
    KeychainFixture.keychainClient(accountAddresses, keyChainId = Some(keychainId)),
    _ => explorer,
    interpreter,
    _ => alreadyValidBlockCursorService
  )

  it should "synchronize on given parameters" in IOAssertion {

    val interpreter = new InterpreterClientMock

    assert(interpreter.getSavedTransaction(accountIdentifier.id).isEmpty)

    val syncParams = mkSyncParams(None)

    for {
      _ <- worker(interpreter).run(syncParams)
    } yield {
      val txs: List[TransactionView] = interpreter.getSavedTransaction(accountIdentifier.id)
      txs should have size 4
    }
  }

  it should "not import confirmed txs when the cursor is already on the last mined block" in IOAssertion {

    val lastMinedBlock = blockchain.last._1
    val interpreter    = new InterpreterClientMock
    val explorer =
      new ExplorerClientMock(blockchain.flatMap(_._2).toMap)

    assert(interpreter.getSavedTransaction(accountIdentifier.id).isEmpty)

    val syncParams = mkSyncParams(Some(lastMinedBlock))

    for {
      _ <- worker(interpreter, explorer).run(syncParams)
    } yield {

      interpreter.getSavedTransaction(accountIdentifier.id) shouldBe empty
      explorer.getConfirmedTransactionsCount = 0
    }
  }

  it should "try to import unconfirmed txs even if blockchain last block is synced" in {

    val lastMinedBlock = blockchain.last._1
    val explorer =
      new ExplorerClientMock(blockchain.flatMap(_._2).toMap, mempool.toMap)

    val syncParams = mkSyncParams(Some(lastMinedBlock))
    val w          = worker(explorer = explorer)
    for {
      _ <- w.run(syncParams)
      _ <- w.run(syncParams)
    } yield {
      explorer.getUnConfirmedTransactionsCount = 2
    }
  }

  def mkSyncParams(
      cursor: Option[Block]
  ): SynchronizationParameters =
    SynchronizationParameters(
      Xpub("xpubtoto"),
      syncId = UUID.randomUUID(),
      scheme = Bip44,
      coin = Coin.Btc,
      blockHash = cursor.map(_.hash),
      walletUid = UUID.randomUUID(),
      lookahead = 20
    )
}
