package co.ledger.cria.itutils
import cats.effect.IO
import co.ledger.cria.domain.adapters.persistence.tee.Combiner
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.CurrentBalance
import co.ledger.cria.itutils.models.GetUtxosResult

final class TestUtilsTee(primary: TestUtils, secondary: TestUtils, combiner: Combiner) extends TestUtils {
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
