package co.ledger.cria.domain.adapters.persistence.wd.queries

import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.implicits._
import co.ledger.cria.domain.models.interpreter.BlockchainBalance
import doobie._
import doobie.implicits._

object WDBalanceQueries extends DoobieLogHandler {

  def getBlockchainBalance(
      accountId: AccountUid
  ): ConnectionIO[BlockchainBalance] = {
    val balanceAndUtxosQuery =
      sql"""
          WITH
            input_usage as (
              SELECT i.previous_tx_hash, i.previous_output_idx, tx2.block_uid
              FROM bitcoin_inputs i
                INNER JOIN bitcoin_transaction_inputs txi
                  ON i.uid = txi.input_uid
                INNER JOIN bitcoin_transactions tx2
                  ON txi.transaction_uid = tx2.transaction_uid
            ),
            confirmed_utxos as (
              SELECT o.amount AS value
              FROM bitcoin_outputs o
                INNER JOIN bitcoin_transactions tx
                  ON o.transaction_hash = tx.hash
                  AND tx.block_uid IS NOT NULL
                LEFT JOIN input_usage iu
                  ON o.transaction_hash = iu.previous_tx_hash
                  AND o.idx = iu.previous_output_idx
              WHERE o.account_uid = $accountId
              AND iu.block_uid IS NULL -- input is unused
            )

          SELECT COALESCE(SUM(value), 0), COALESCE(COUNT(value), 0)
          FROM confirmed_utxos
      """
        .query[(BigInt, Int)]
        .unique

    val receivedAndSentQuery =
      sql"""SELECT
              COALESCE(SUM(CASE WHEN type = 'RECEIVE' THEN ('x' || lpad(amount, 16, '0'))::bit(64)::bigint ELSE 0 END), 0) as receive,
              COALESCE(SUM(CASE WHEN type = 'SEND'    THEN ('x' || lpad(amount, 16, '0'))::bit(64)::bigint ELSE 0 END), 0) as send,
              COALESCE(SUM(CASE WHEN type = 'SEND'    THEN ('x' || lpad(fees, 16, '0'))::bit(64)::bigint ELSE 0 END), 0) as fees
            FROM operations
            WHERE block_uid IS NOT NULL
             AND account_uid = $accountId
         """
        .query[(BigInt, BigInt, BigInt)]
        .unique

    for {
      result1 <- balanceAndUtxosQuery
      result2 <- receivedAndSentQuery
    } yield {
      val (balance, utxos)       = result1
      val (received, sent, fees) = result2
      BlockchainBalance(balance, utxos, received, sent, fees)
    }
  }

  def getUnconfirmedBalance(
      accountId: AccountUid
  ): ConnectionIO[BigInt] =
    sql"""
          WITH
            new_utxos as (
              SELECT COALESCE(SUM(o.amount), 0) as value
              FROM bitcoin_outputs o
                INNER JOIN bitcoin_transactions tx
                  ON o.transaction_hash = tx.hash
                  AND tx.block_uid IS NULL
                LEFT JOIN bitcoin_inputs i
                  ON o.transaction_hash = i.previous_tx_hash
                  AND o.idx = i.previous_output_idx
              WHERE o.account_uid = $accountId
              AND i.uid IS NULL
            ),
            used_utxos as (
              SELECT COALESCE(SUM(i.amount), 0) as value
              FROM bitcoin_transaction_inputs txi
                INNER JOIN bitcoin_transactions tx
                  ON tx.transaction_uid = txi.transaction_uid
                  AND tx.block_uid IS NULL
                INNER JOIN bitcoin_inputs i
                  ON txi.input_uid = i.uid
                INNER JOIN bitcoin_outputs o
                  ON o.transaction_hash = i.previous_tx_hash
                  AND o.idx = i.previous_output_idx
              WHERE o.account_uid = $accountId
            )

          SELECT new_utxos.value - used_utxos.value as pending_amount
          FROM new_utxos, used_utxos
      """
      .query[BigInt]
      .unique
}
