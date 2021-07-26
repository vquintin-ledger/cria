package co.ledger.cria.e2e.incremental

import cats.Order
import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{ContextShift, IO, Timer}
import cats.kernel.Semigroup
import co.ledger.cria.domain.CriaModule
import co.ledger.cria.domain.models.{SynchronizationParameters, TxHash}
import co.ledger.cria.domain.models.interpreter.{
  BlockHash,
  BlockView,
  Coin,
  Confirmation,
  TransactionView
}
import co.ledger.cria.domain.services.ExplorerClient
import co.ledger.cria.e2e.{E2EHelper, KeychainHelper, RegisterRequest, SyncResult, TestCase}
import co.ledger.cria.itutils.ContainerSpec
import co.ledger.cria.logging.CriaLogContext
import org.scalatest.BeforeAndAfter
import co.ledger.cria.utils.IOAssertion
import org.scalacheck.Gen
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import shapeless.tag.@@
import cats.implicits._

final class IncrementalSyncE2ETest()
    extends AnyPropSpec
    with ContainerSpec
    with KeychainHelper
    with E2EHelper
    with BeforeAndAfter
    with ScalaCheckPropertyChecks {

  private val testCase: TestCase = TestCase.readJson("test-accounts-btc.json")(1)

  private type Range = (Min[Long], Max[Long])

  private val blockRange: Ref[IO, Option[Range]] = Ref[IO].of[Option[Range]](None).unsafeRunSync()

  private val mutex: Semaphore[IO] = Semaphore[IO](1).unsafeRunSync()

  before {
    getBlockRange(testCase.registerRequest).unsafeRunSync()
  }

  private val blockLimitGen: Gen[Long] =
    Gen
      .delay(Gen.const(blockRange.get.unsafeRunSync()))
      .flatMap((opt: Option[Range]) => opt.fold(Gen.fail[Range])(Gen.const))
      .flatMap((r: Range) => Gen.choose(r._1.value, r._2.value))

  property("Can update an existing synchronization") {
    forAll(blockLimitGen) { blockLimit =>
      IOAssertion {
        mutex.withPermit {
          for {
            _          <- setupDB
            _          <- setupAccount(testCase.registerRequest)
            syncParams <- makeSyncParameters(testCase.registerRequest)
            _          <- log.info(s"Running cria up to block ${blockLimit}")
            _          <- runCriaUpTo(syncParams, blockLimit)
            _          <- log.info(s"Running cria to latest block")
            actual     <- runCriaSuccessfully(syncParams)
          } yield {
            assert(actual == testCase.expected)
          }
        }
      }
    }
  }

  private def runCriaUpTo(syncParams: SynchronizationParameters, blockHeight: Long): IO[Unit] =
    appResources.use { cr =>
      val module = new CriaModule(
        cr.persistenceFacade,
        cr.keychainClient,
        limitedExplorerClient(cr.explorerClient, blockHeight)
      )
      module.synchronizer.run(syncParams).flatMap(adaptCriaResult)
    }

  private def runCriaSuccessfully(syncParams: SynchronizationParameters): IO[SyncResult] =
    appResources.use { cr =>
      val module = new CriaModule(cr.persistenceFacade, cr.keychainClient, cr.explorerClient)
      for {
        _ <- module.synchronizer.run(syncParams).flatMap(adaptCriaResult)
        syncResult <- getSyncResult(
          syncParams.accountUid.value,
          syncParams.keychainId,
          syncParams.coin
        )
      } yield syncResult
    }

  private def getBlockRange(registerRequest: RegisterRequest): IO[Option[Range]] =
    appResources
      .map { cr =>
        new CriaModule(
          cr.persistenceFacade,
          cr.keychainClient,
          savingExplorerClient(cr.explorerClient, blockRange)
        )
      }
      .use { module =>
        for {
          _          <- setupDB
          _          <- setupAccount(registerRequest)
          params     <- makeSyncParameters(registerRequest)
          syncResult <- module.synchronizer.run(params)
          _          <- adaptCriaResult(syncResult)
          range      <- blockRange.get
        } yield range
      }

  private def savingExplorerClient(
      getExplorerClient: Coin => ExplorerClient,
      blockRange: Ref[IO, Option[Range]]
  )(c: Coin) =
    new ExplorerClient {
      private val client = getExplorerClient(c)

      override def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView] =
        client.getCurrentBlock

      override def getBlock(
          hash: BlockHash
      )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]] =
        client.getBlock(hash)

      override def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[BlockHash])(
          implicit
          cs: ContextShift[IO],
          t: Timer[IO],
          lc: CriaLogContext
      ): fs2.Stream[IO, TransactionView @@ Confirmation.Confirmed] =
        client
          .getConfirmedTransactions(addresses, blockHash)
          .evalTap { t =>
            val update = t.block.map(bv => (Min(bv.height), Max(bv.height)))
            blockRange.update(_ |+| update)
          }

      override def getUnconfirmedTransactions(addresses: Set[String])(implicit
          cs: ContextShift[IO],
          t: Timer[IO],
          lc: CriaLogContext
      ): fs2.Stream[IO, TransactionView @@ Confirmation.Unconfirmed] =
        client.getUnconfirmedTransactions(addresses)

      override def getTransaction(
          transactionHash: TxHash
      )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]] =
        client.getTransaction(transactionHash)
    }

  private def limitedExplorerClient(getExplorerClient: Coin => ExplorerClient, blockHeight: Long)(
      c: Coin
  ): ExplorerClient =
    new ExplorerClient {
      private val delegate = getExplorerClient(c)

      override def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[BlockView] =
        delegate.getCurrentBlock

      override def getBlock(
          hash: BlockHash
      )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[BlockView]] =
        delegate.getBlock(hash)

      override def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[BlockHash])(
          implicit
          cs: ContextShift[IO],
          t: Timer[IO],
          lc: CriaLogContext
      ): fs2.Stream[IO, TransactionView @@ Confirmation.Confirmed] =
        delegate
          .getConfirmedTransactions(addresses, blockHash)
          .filter(t => t.block.forall(bv => bv.height <= blockHeight))

      override def getUnconfirmedTransactions(addresses: Set[String])(implicit
          cs: ContextShift[IO],
          t: Timer[IO],
          lc: CriaLogContext
      ): fs2.Stream[IO, TransactionView @@ Confirmation.Unconfirmed] =
        delegate.getUnconfirmedTransactions(addresses)

      override def getTransaction(
          transactionHash: TxHash
      )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[TransactionView]] =
        delegate.getTransaction(transactionHash)
    }

  private case class Min[A](value: A)

  private implicit def semigroupMin[A](implicit order: Order[A]): Semigroup[Min[A]] =
    new Semigroup[Min[A]] {
      override def combine(x: Min[A], y: Min[A]): Min[A] = Min(order.min(x.value, y.value))
    }

  private case class Max[A](value: A)

  private implicit def semigroupMax[A](implicit order: Order[A]): Semigroup[Max[A]] =
    new Semigroup[Max[A]] {
      override def combine(x: Max[A], y: Max[A]): Max[A] = Max(order.max(x.value, y.value))
    }

}
