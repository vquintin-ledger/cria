package co.ledger.cria.e2e.recovery

import cats.effect.concurrent.Ref
import cats.effect.{IO, Resource}
import cats.implicits._
import co.ledger.cria.domain.CriaModule
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.models.SynchronizationParameters
import co.ledger.cria.e2e.{E2EHelper, KeychainHelper, SyncResult, TestCase}
import co.ledger.cria.itutils.ContainerSpec
import co.ledger.cria.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpec

final class RecoveryE2ETest
    extends AnyFlatSpec
    with ContainerSpec
    with KeychainHelper
    with E2EHelper {

  private val testCase: TestCase = TestCase.readJson("test-accounts-btc.json").head

  "A failure" should "not impact a later run" in IOAssertion {
    getNumberOfIO.flatMap { nbIOs =>
      val expected = testCase.expected
      (0 until nbIOs).toList.traverse { failureIndex =>
        for {
          _          <- setupDB
          _          <- setupAccount(testCase.registerRequest)
          syncParams <- makeSyncParameters(testCase.registerRequest)
          failure    <- runCriaWithFailure(syncParams, failureIndex).attempt
          actual     <- runCriaSuccessfully(syncParams)
        } yield {
          assert(failure.isLeft)
          assert(actual == expected)
        }
      }
    }
  }

  private def runCriaWithFailure(
      syncParams: SynchronizationParameters,
      failureIndex: Int
  ): IO[Unit] =
    Ref[IO]
      .of(failureIndex)
      .flatMap { bombClock =>
        val checkBombClock = bombClock
          .getAndUpdate(_ - 1)
          .flatMap(clock => IO.raiseWhen(clock == 0)(new RuntimeException("BOOM!!")))
        makeCriaModuleWithBeforeAction(checkBombClock)
          .use(module => module.synchronizer.run(syncParams))
          .flatMap(adaptCriaResult)
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

  private def getNumberOfIO: IO[Int] = {
    def count(counter: Ref[IO, Int]): IO[Unit] = {
      val increment = counter.update(_ + 1)
      makeCriaModuleWithBeforeAction(increment).use { module =>
        for {
          _              <- setupDB
          _              <- setupAccount(testCase.registerRequest)
          syncParameters <- makeSyncParameters(testCase.registerRequest)
          _              <- module.synchronizer.run(syncParameters)
        } yield ()
      }
    }

    for {
      counter <- Ref[IO].of(0)
      _       <- count(counter)
      nbIOs   <- counter.get
    } yield nbIOs
  }

  private def makeCriaModuleWithBeforeAction(action: IO[Unit]): Resource[IO, CriaModule] =
    appResources
      .map { cr =>
        val pf = new PersistenceFacadeAccessRunBefore(cr.persistenceFacade, action)
        val kc = new KeychainClientRunBefore(cr.keychainClient, action)
        val ec = (c: Coin) => new ExplorerClientRunBefore(cr.explorerClient(c), action)
        new CriaModule(pf, kc, ec)
      }
}
