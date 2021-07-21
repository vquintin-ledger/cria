package co.ledger.cria

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.config.Config
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource

import java.time.Instant
import java.util.UUID
import co.ledger.cria.clients.explorer.ExplorerHttpClient
import co.ledger.cria.clients.protocol.http.Clients
import co.ledger.cria.domain.adapters.explorer.ExplorerClientAdapter
import co.ledger.cria.domain.adapters.keychain.KeychainClientMock
import co.ledger.cria.domain.models.SynchronizationParameters
import co.ledger.cria.domain.models.SynchronizationResult.SynchronizationSuccess
import co.ledger.cria.domain.models.account.{Account, AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{Coin, SyncId}
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.domain.services
import co.ledger.cria.domain.services.{CursorStateService, Synchronizer}
import co.ledger.cria.domain.services.interpreter.InterpreterClientMock
import co.ledger.cria.utils.IOAssertion

import scala.concurrent.ExecutionContext

class SynchronizerIT extends AnyFlatSpecLike with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  IOAssertion {
    Clients.htt4s
      .use { httpClient =>
        val keychainId = KeychainId(UUID.randomUUID())

        val keychainClient = new KeychainClientMock

        val explorerClient = ExplorerClientAdapter.explorerForCoin(
          new ExplorerHttpClient(httpClient, conf.explorer, _)
        ) _

        val interpreterClient = new InterpreterClientMock

        val cursorStateService: Coin => CursorStateService[IO] =
          c =>
            services
              .CursorStateService(explorerClient(c), interpreterClient)
              .getLastValidState(_, _, _)

        val syncId = SyncId(UUID.randomUUID())

        val args: SynchronizationParameters =
          SynchronizationParameters(
            keychainId,
            Coin.Btc,
            syncId,
            None,
            AccountUid(UUID.randomUUID().toString),
            WalletUid(UUID.randomUUID().toString)
          )

        val worker = new Synchronizer(
          keychainClient,
          explorerClient,
          interpreterClient,
          cursorStateService
        )

        val account = Account(
          args.accountUid,
          keychainId,
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
              interpreterClient.getSavedTransaction(
                account.accountUid
              ) should have size expectedTxsSize

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
