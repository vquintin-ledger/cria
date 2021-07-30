package co.ledger.cria.itutils

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.config.PersistenceConfig
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.CurrentBalance
import co.ledger.cria.logging.{IOLogger, LogContext}

trait TestUtils {

  def setupAccount(accountUid: AccountUid, walletUid: WalletUid): IO[Int]

  def getOperationCount(
      accountId: AccountUid
  ): IO[Int]
/*
  def getUtxos(
      accountId: AccountUid,
      limit: Int,
      offset: Int,
      sort: Sort
  ): IO[GetUtxosResult]
*/
  def getBalance(accountId: AccountUid): IO[CurrentBalance]

  def migrate: IO[Unit]

  def clean: IO[Unit]
}

object TestUtils {
  def fromConfig(c: PersistenceConfig, log: IOLogger)(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: LogContext
  ): Resource[IO, TestUtils] =
    PersistenceConfig.foldM[Resource[IO, *], TestUtils](
      WDTestUtils.apply,
      LamaTestUtils.apply,
      (t1, t2, conf) => Resource.pure[IO, TestUtils](TestUtilsTee(t1, t2, conf, log))
    )(c)
}
