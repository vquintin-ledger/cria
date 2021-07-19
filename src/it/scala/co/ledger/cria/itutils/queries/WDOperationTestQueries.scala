package co.ledger.cria.itutils.queries

import co.ledger.cria.itutils.models.{ConfirmedUtxo, Utxo}
import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream

object WDOperationTestQueries extends DoobieLogHandler {

  def countUTXOs(accountId: AccountUid): ConnectionIO[Int] =
    sql"""SELECT COUNT(*)
          FROM output o
            LEFT JOIN input i
              ON o.account_uid = i.account_uid
              AND o.address = i.address
              AND o.output_index = i.output_index
              AND o.hash = i.output_hash
            INNER JOIN transaction tx
              ON o.account_uid = tx.account_uid
              AND o.hash = tx.hash
          WHERE o.account_uid = $accountId
            AND o.derivation IS NOT NULL
            AND i.address IS NULL
            AND tx.block_hash IS NOT NULL
       """
      .query[Int]
      .unique

  def fetchConfirmedUTXOs(
      accountId: AccountUid,
      sort: Sort = Sort.Ascending,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ): Stream[ConnectionIO, ConfirmedUtxo] = {
    val orderF  = Fragment.const(s"ORDER BY tx.block_time $sort, tx.hash $sort")
    val limitF  = limit.map(l => fr"LIMIT $l").getOrElse(Fragment.empty)
    val offsetF = offset.map(o => fr"OFFSET $o").getOrElse(Fragment.empty)

    val query =
      sql"""SELECT tx.block_height, tx.confirmations, tx.hash, o.output_index, o.value, o.address, o.script_hex, o.change_type, o.derivation, tx.block_time
            FROM output o
              LEFT JOIN input i
                ON o.account_uid = i.account_uid
                AND o.address = i.address
                AND o.output_index = i.output_index
			          AND o.hash = i.output_hash
              INNER JOIN transaction tx
                ON o.account_uid = tx.account_uid
                AND o.hash = tx.hash
                AND tx.block_hash IS NOT NULL
            WHERE o.account_uid = $accountId
              AND o.derivation IS NOT NULL
              AND i.address IS NULL
         """ ++ orderF ++ limitF ++ offsetF
    query.query[ConfirmedUtxo].stream
  }

  def fetchUnconfirmedUTXOs(
      accountId: AccountUid
  ): Stream[ConnectionIO, Utxo] =
    sql"""SELECT tx.hash, o.output_index, o.value, o.address, o.script_hex, o.change_type, o.derivation, tx.received_at
            FROM output o
              LEFT JOIN input i
                ON o.account_uid = i.account_uid
                AND o.address = i.address
                AND o.output_index = i.output_index
			          AND o.hash = i.output_hash
              INNER JOIN transaction tx
                ON o.account_uid = tx.account_uid
                AND o.hash = tx.hash
                AND tx.block_hash IS NULL
            WHERE o.account_uid = $accountId
              AND o.derivation IS NOT NULL
              AND i.address IS NULL
         """.query[Utxo].stream

  def countOperations(accountId: AccountUid): ConnectionIO[Int] =
    sql"""SELECT COUNT(*) FROM operations WHERE account_uid = $accountId"""
      .query[Int]
      .unique

}
