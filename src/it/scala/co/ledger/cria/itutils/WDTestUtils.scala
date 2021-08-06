package co.ledger.cria.itutils

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.domain.adapters.persistence.wd.{DBType, WalletDaemonDb}
import co.ledger.cria.domain.adapters.persistence.wd.queries.WDBalanceQueries
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.CurrentBalance
import co.ledger.cria.itutils.models.GetUtxosResult
import co.ledger.cria.itutils.queries.{AccountTestQueries, WDOperationTestQueries}
import co.ledger.cria.utils.{DbUtils, ResourceUtils}
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import shapeless.tag
import shapeless.tag.@@

final class WDTestUtils private (
    conf: WalletDaemonDb,
    db: Transactor[IO] @@ DBType.WD,
    criaExtra: Transactor[IO] @@ DBType.Temporary
) extends TestUtils {

  override def setupAccount(accountUid: AccountUid, walletUid: WalletUid): IO[Int] =
    AccountTestQueries.addAccount(accountUid, walletUid).transact(db)

  override def getOperationCount(
      accountId: AccountUid
  ): IO[Int] = {
    WDOperationTestQueries.countOperations(accountId).transact(db)
  }

  override def getUtxos(
      accountId: AccountUid,
      limit: Int,
      offset: Int,
      sort: Sort
  ): IO[GetUtxosResult] =
    for {
      utxos <- WDOperationTestQueries
        .fetchConfirmedUTXOs(accountId, sort, Some(limit + 1), Some(offset))
        .transact(criaExtra)
        .compile
        .toList

      total <- WDOperationTestQueries.countUTXOs(accountId).transact(criaExtra)

    } yield {
      // We get 1 more than necessary to know if there's more, then we return the correct number
      GetUtxosResult(utxos.slice(0, limit), total, truncated = utxos.size > limit)
    }

  override def getBalance(accountId: AccountUid): IO[CurrentBalance] =
    (for {
      blockchainBalance <- WDBalanceQueries.getBlockchainBalance(accountId)
      mempoolBalance    <- WDBalanceQueries.getUnconfirmedBalance(accountId)
    } yield {
      CurrentBalance(
        blockchainBalance.balance,
        blockchainBalance.utxos,
        blockchainBalance.received,
        blockchainBalance.netSent + blockchainBalance.fees,
        mempoolBalance
      )
    }).transact(db)

  private lazy val flywayWalletDaemon: Flyway =
    DbUtils.flyway(conf.walletDaemon, "classpath:/db/wd_dump/")
  private lazy val flywayCriaExtra: Flyway =
    DbUtils.flyway(conf.criaExtra, "classpath:/db/wd_temporary/")

  override def migrate: IO[Unit] = IO(flywayWalletDaemon.migrate()) *> IO(flywayCriaExtra.migrate())

  override def clean: IO[Unit] = IO(flywayWalletDaemon.clean()) *> IO(flywayCriaExtra.clean())
}

object WDTestUtils {
  def apply(
      conf: WalletDaemonDb
  )(implicit cs: ContextShift[IO], t: Timer[IO]): Resource[IO, TestUtils] =
    for {
      walletDaemonTransactor <- ResourceUtils.postgresTransactor(conf.walletDaemon)
      criaExtraTransactor    <- ResourceUtils.postgresTransactor(conf.criaExtra)
    } yield new WDTestUtils(
      conf,
      tag[DBType.WD](walletDaemonTransactor),
      tag[DBType.Temporary](criaExtraTransactor)
    )
}
