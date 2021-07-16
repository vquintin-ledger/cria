package co.ledger.cria.domain.services.interpreter

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{AccountTxView, BlockView, TransactionView}
import co.ledger.cria.logging.CriaLogContext
import fs2.Pipe

trait TransactionService {

  def saveTransactions(implicit
                       cs: ContextShift[IO],
                       lc: CriaLogContext
                      ): Pipe[IO, AccountTxView, Int]

  def removeFromCursor(accountId: AccountUid, blockHeight: Long): IO[Int]

  def getLastBlocks(accountId: AccountUid): fs2.Stream[IO, BlockView]

  def deleteUnconfirmedTransaction(accountId: AccountUid, hash: TxHash): IO[String]

  def fetchTransactions(
                         accountId: AccountUid,
                         sort: Sort,
                         hashes: NonEmptyList[TxHash]
                       ): fs2.Stream[IO, TransactionView]
}
