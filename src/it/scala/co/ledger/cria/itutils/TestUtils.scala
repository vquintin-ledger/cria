package co.ledger.cria.itutils

import cats.effect.IO
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
}
