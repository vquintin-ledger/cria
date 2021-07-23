package co.ledger.cria.domain.adapters.persistence.lama.queries

import java.time.Instant
import cats.data.NonEmptyList
import cats.implicits._
import co.ledger.cria.domain.adapters.persistence.lama.models.OperationToSave
import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.implicits._
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream

object LamaOperationQueries extends DoobieLogHandler {

  case class Tx(
      id: String,
      hash: TxHash,
      receivedAt: Instant,
      lockTime: Long,
      fees: BigInt,
      block: Option[BlockView],
      confirmations: Int
  )

  case class Op(
      uid: Operation.UID,
      accountId: AccountUid,
      hash: TxHash,
      operationType: OperationType,
      amount: BigInt,
      fees: BigInt,
      time: Instant,
      blockHeight: Option[Long]
  )

  case class OpWithoutDetails(op: Op, tx: Tx)

  case class OperationDetails(
      txHash: TxHash,
      inputs: List[InputView],
      outputs: List[OutputView]
  )

  def fetchUncomputedTransactionAmounts(
      accountId: AccountUid,
      sort: Sort
  ): Stream[ConnectionIO, TransactionAmounts] =
    (sql"""SELECT tx.account_id,
                 tx.hash,
                 tx.block_hash,
                 tx.block_height,
                 tx.block_time,
                 tx.fees,
                 COALESCE(tx.input_amount, 0),
                 COALESCE(tx.output_amount, 0),
                 COALESCE(tx.change_amount, 0)
          FROM transaction_amount tx
            LEFT JOIN operation op
              ON op.hash = tx.hash
              AND op.account_id = tx.account_id
          WHERE op.block_hash IS null --either the operation doesn't exist or it's mempool
          AND tx.account_id = $accountId
       """ ++ Fragment
      .const(s"ORDER BY tx.block_time $sort, tx.hash $sort"))
      .query[TransactionAmounts]
      .stream

  def saveOperations(operation: List[OperationToSave]): ConnectionIO[Int] = {
    val query =
      """INSERT INTO operation (
         uid, account_id, hash, operation_type, amount, fees, time, block_hash, block_height
       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT ON CONSTRAINT operation_pkey DO NOTHING
    """
    Update[OperationToSave](query).updateMany(operation)
  }

  def deleteUnconfirmedOperations(accountId: AccountUid): doobie.ConnectionIO[Int] = {
    sql"""DELETE FROM operation
         WHERE account_id = $accountId
         AND block_height IS NULL
       """.update.run
  }

  def flagBelongingInputs(
      accountId: AccountUid,
      addresses: NonEmptyList[AccountAddress]
  ): ConnectionIO[Int] = {
    val queries = addresses.map { addr =>
      sql"""UPDATE input
            SET derivation = ${addr.derivation.toList}
            WHERE account_id = $accountId
            AND address = ${addr.accountAddress}
         """
    }

    queries.traverse(_.update.run).map(_.toList.sum)
  }

  def flagBelongingOutputs(
      accountId: AccountUid,
      addresses: NonEmptyList[AccountAddress],
      changeType: ChangeType
  ): ConnectionIO[Int] = {
    val queries = addresses.map { addr =>
      sql"""UPDATE output
            SET change_type = $changeType,
                derivation = ${addr.derivation.toList}
            WHERE account_id = $accountId
            AND address = ${addr.accountAddress}
         """
    }

    queries.traverse(_.update.run).map(_.toList.sum)
  }

  def removeFromCursor(accountId: AccountUid, blockHeight: Long): ConnectionIO[Int] =
    sql"""DELETE from operation
          WHERE account_id = $accountId
          AND (block_height >= $blockHeight
              OR block_height IS NULL)
       """.update.run
}
