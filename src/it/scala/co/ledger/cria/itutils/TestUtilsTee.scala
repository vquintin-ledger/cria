package co.ledger.cria.itutils
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.adapters.persistence.tee.{Combiner, OnDiffAction, TeeConcurrency, TeeConfig}
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.CurrentBalance
import co.ledger.cria.itutils.models.GetUtxosResult
import co.ledger.cria.logging.{IOLogger, LogContext}

final class TestUtilsTee private (primary: TestUtils, secondary: TestUtils, combiner: Combiner) extends TestUtils {
  override def setupAccount(accountUid: AccountUid, walletUid: WalletUid): IO[Int] =
    combiner.combineAction(primary.setupAccount(accountUid, walletUid), secondary.setupAccount(accountUid, walletUid))

  override def getOperationCount(accountId: AccountUid): IO[Int] =
    combiner.combineAction(primary.getOperationCount(accountId), secondary.getOperationCount(accountId))

  override def getUtxos(accountId: AccountUid, limit: Int, offset: Int, sort: Sort): IO[GetUtxosResult] =
    combiner.combineAction(primary.getUtxos(accountId, limit, offset, sort), secondary.getUtxos(accountId, limit, offset, sort))

  override def getBalance(accountId: AccountUid): IO[CurrentBalance] =
    combiner.combineAction(primary.getBalance(accountId), secondary.getBalance(accountId))

  override def migrate: IO[Unit] =
    combiner.combineAction(primary.migrate, secondary.migrate)

  override def clean: IO[Unit] =
    combiner.combineAction(primary.clean, secondary.clean)
}

object TestUtilsTee {
  def apply(primary: TestUtils, secondary: TestUtils, teeConfig: TeeConfig, log: IOLogger)(implicit lc: LogContext, cs: ContextShift[IO]): TestUtils = {
    val onDiff = teeConfig.onDiff match {
      case OnDiffAction.Fail => Combiner.failOnDiff
      case OnDiffAction.Log => Combiner.logOnDiff(log)
    }

    val combiner = teeConfig.concurrency match {
      case TeeConcurrency.Parallel => Combiner.parallel(onDiff)
      case TeeConcurrency.Sequential => Combiner.sequential(onDiff)
    }

    new TestUtilsTee(primary, secondary, combiner)
  }
}