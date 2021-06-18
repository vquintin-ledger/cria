package co.ledger.lama.bitcoin.worker

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.clients.grpc.mocks.{InterpreterClientMock, KeychainClientMock}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerHttpClient
import co.ledger.lama.bitcoin.common.models.Scheme
import co.ledger.lama.bitcoin.common.models.keychain.AccountKey.Xpub
import co.ledger.lama.bitcoin.worker.SynchronizationResult.{SynchronizationSuccess}
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services.{CursorStateService}
import co.ledger.lama.common.models._
import co.ledger.lama.common.services.Clients
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

class WorkerIT extends AnyFlatSpecLike with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  IOAssertion {
    Clients.htt4s
      .use { httpClient =>
        val keychainId = UUID.randomUUID()

        val keychainClient = new KeychainClientMock(Some(keychainId))

        val explorerClient = new ExplorerHttpClient(httpClient, conf.explorer, _)

        val interpreterClient = new InterpreterClientMock

        val cursorStateService: Coin => CursorStateService[IO] =
          c => CursorStateService(explorerClient(c), interpreterClient).getLastValidState(_, _, _)

        val args: SynchronizationParameters =
          SynchronizationParameters(
            Xpub("xpubtoto"),
            Scheme.Bip44,
            Coin.Btc,
            UUID.randomUUID(),
            None,
            UUID.randomUUID(),
            20
          )

        val worker = new Worker(
          keychainClient,
          explorerClient,
          interpreterClient,
          cursorStateService
        )

        val account = Account(
          keychainId.toString,
          CoinFamily.Bitcoin,
          Coin.Btc
        )

        worker
          .run(args)
          .map { result =>
            it should "have 35 used addresses for the account" in {
              keychainClient.usedAddresses.size shouldBe 19 + 35
            }

            val expectedTxsSize         = 73
            val expectedLastBlockHeight = 644553L

            it should s"have synchronized $expectedTxsSize txs with last blockHeight > $expectedLastBlockHeight" in {
              interpreterClient.getSavedTransaction(account.id) should have size expectedTxsSize

              result match {
                case SynchronizationSuccess(_, newCursor) =>
                  newCursor.height should be > expectedLastBlockHeight
                  newCursor.time should be > Instant.parse("2020-08-20T13:01:16Z")
                case _ =>
                  fail("synchronization should succeed")
              }
            }
          }
      }
  }

}
