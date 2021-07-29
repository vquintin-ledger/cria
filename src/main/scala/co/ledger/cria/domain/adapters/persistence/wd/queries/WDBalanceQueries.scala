package co.ledger.cria.domain.adapters.persistence.wd.queries

import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.implicits._
import co.ledger.cria.domain.models.interpreter.BlockchainBalance
import doobie._
import doobie.implicits._

object WDBalanceQueries extends DoobieLogHandler {

  //FIXME: rework on WD tables instead of temp lama tables...
  def getBlockchainBalance(
      accountId: AccountUid
  ): ConnectionIO[BlockchainBalance] = {
    val balanceAndUtxosQuery =
      sql"""
          WITH confirmed_utxos as (
            SELECT o.amount AS value
            FROM bitcoin_outputs o
              INNER JOIN bitcoin_transactions tx
                ON o.transaction_hash = tx.hash
                AND tx.block_uid IS NOT NULL
              LEFT JOIN bitcoin_inputs i
                ON o.transaction_hash = i.previous_tx_hash
                AND o.idx = i.previous_output_idx
            WHERE o.account_uid = $accountId
            AND i.uid IS NULL
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
          WITH unconfirmed_utxos as (
            SELECT o.amount
            FROM bitcoin_outputs o
              INNER JOIN bitcoin_transactions tx
                ON o.transaction_hash = tx.hash
                AND tx.block_uid IS NULL
              LEFT JOIN bitcoin_inputs i
                ON o.transaction_hash = i.previous_tx_hash
                AND o.idx = i.previous_output_idx
            WHERE o.account_uid = $accountId
            AND i.uid IS NULL
          )

          SELECT COALESCE(SUM(amount), 0) as pending_amount
          FROM unconfirmed_utxos
      """
      .query[BigInt]
      .unique
}
