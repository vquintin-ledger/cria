package co.ledger.cria.e2e

import cats.effect.IO
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.itutils.models.keychain.AccountKey.Xpub
import co.ledger.cria.itutils.models.keychain.Scheme
import co.ledger.cria.clients.explorer.v3.models.circeImplicits._
import co.ledger.cria.domain.models.{Sort, SynchronizationResult}
import co.ledger.cria.domain.models.SynchronizationResult.{
  SynchronizationFailure,
  SynchronizationSuccess
}
import co.ledger.cria.domain.models.account.{Account, AccountUid}
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.itutils.ContainerSpec
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.parser.decode

import java.util.UUID
import scala.io.Source

trait E2EHelper { cfs: ContainerSpec =>

  def getSyncResult(accountUid: String, keychainId: KeychainId, coin: Coin): IO[SyncResult] =
    testResources.use { res =>
      val account = Account(AccountUid(accountUid), keychainId, coin)
      for {
        opsSize   <- res.testUtils.getOperationCount(account.accountUid)
        utxosSize <- res.testUtils.getUtxos(account.accountUid, 20, 0, Sort.Ascending)
        balance   <- res.testUtils.getBalance(account.accountUid)
      } yield SyncResult(
        opsSize,
        utxosSize.total,
        balance.balance.longValue,
        balance.received.longValue,
        balance.sent.longValue
      )
    }

  def adaptCriaResult(r: SynchronizationResult): IO[Unit] =
    r match {
      case SynchronizationSuccess(_, _)         => IO.unit
      case SynchronizationFailure(_, throwable) => IO.raiseError(throwable)
    }
}

case class TestCase(registerRequest: RegisterRequest, expected: SyncResult)

object TestCase {
  implicit val decoderTestCase: Decoder[TestCase] = deriveConfiguredDecoder[TestCase]

  def readJson(file: String): List[TestCase] = {
    val raw = Source.fromResource(file).getLines().foldLeft("")(_ + _)
    decode[List[TestCase]](raw)
      .fold(err => throw new IllegalArgumentException(s"Could not parse $file", err), identity)
  }
}

case class RegisterRequest(
    accountKey: Xpub,
    scheme: Scheme,
    lookaheadSize: Int,
    coin: Coin,
    syncId: UUID,
    accountUid: String,
    walletUid: String,
    accountIndex: Int,
    metadata: String
)

object RegisterRequest {
  implicit val decoderXpub: Decoder[Xpub] = deriveConfiguredDecoder[Xpub]

  implicit val decoderRegisterRequest: Decoder[RegisterRequest] =
    deriveConfiguredDecoder[RegisterRequest]
}

case class SyncResult(
    opsSize: Int,
    utxosSize: Int,
    balance: Long,
    amountReceived: Long,
    amountSent: Long
)

object SyncResult {
  implicit val decoderSyncResult: Decoder[SyncResult] = deriveConfiguredDecoder[SyncResult]
}
