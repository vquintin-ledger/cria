package co.ledger.cria.domain.adapters.explorer.v2

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.App
import co.ledger.cria.clients.explorer.v2
import co.ledger.cria.clients.explorer.v3
import co.ledger.cria.config.ExplorerConfig.{ExplorerV2, ExplorerV3}
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.logging.DefaultContextLogging
import co.ledger.cria.utils.IOAssertion
import org.http4s.Uri
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.duration

class ExplorerClientAdapterTest extends AnyFunSuite with DefaultContextLogging {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  implicit val t: Timer[IO] = IO.timer(ExecutionContext.global)

  private val config = ExplorerV2(
    http = v2.models.ExplorerConfig(
      Uri.unsafeFromString("https://explorers.api.vault.ledger.com"),
      20,
      500,
      Duration(60, duration.SECONDS)
    ),
    fallback = ExplorerV3(
      http = v3.models.ExplorerConfig(
        Uri.unsafeFromString("https://explorers.api.vault.ledger.com"),
        20,
        500,
        Duration(60, duration.SECONDS)
      )
    )
  )

  test("Can get transactions on addresses") {
    IOAssertion {
      App
        .makeExplorerClient(config)
        .use { getExplorerClient =>
          val explorerClient = getExplorerClient(Coin.Btc)
          explorerClient
            .getConfirmedTransactions(List("1Hc7EofusKsUrNPhbp1PUMkH6wfDohfDBd"), None)
            .foldMap(_ => 1)
            .compile
            .lastOrError
            .map { nbTransactions =>
              assert(nbTransactions > 0)
            }
        }
    }
  }
}
