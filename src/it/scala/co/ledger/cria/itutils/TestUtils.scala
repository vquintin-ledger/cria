package co.ledger.cria.itutils

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.config.PersistenceConfig
import co.ledger.cria.domain.adapters.persistence.tee.Combiner
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.CurrentBalance
import co.ledger.cria.itutils.models.GetUtxosResult

trait TestUtils {

  def setupAccount(accountUid: AccountUid, walletUid: WalletUid): IO[Int]

  def getOperationCount(
                         accountId: AccountUid
                       ): IO[Int]

  def getUtxos(
                accountId: AccountUid,
                limit: Int,
                offset: Int,
                sort: Sort
              ): IO[GetUtxosResult]

  def getBalance(accountId: AccountUid): IO[CurrentBalance]

  def migrate: IO[Unit]

  def clean: IO[Unit]
}

object TestUtils {
  def fromConfig(c: PersistenceConfig)(implicit cs: ContextShift[IO], t: Timer[IO]): Resource[IO, TestUtils] =
    PersistenceConfig.foldM[Resource[IO, *], TestUtils](
      WDTestUtils.apply,
      LamaTestUtils.apply,
      (t1, t2) => Resource.pure[IO, TestUtils](new TestUtilsTee(t1, t2, Combiner.sequential(Combiner.failOnDiff)))
    )(c)
}
