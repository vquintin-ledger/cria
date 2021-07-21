package co.ledger.cria.domain.adapters.persistence.lama.queries

import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.implicits._
import co.ledger.cria.domain.models.interpreter.BlockchainBalance
import doobie._
import doobie.implicits._

object BalanceQueries extends DoobieLogHandler {

  def getBlockchainBalance(
      accountId: AccountUid
  ): ConnectionIO[BlockchainBalance] = {
    val balanceAndUtxosQuery =
      sql"""
          WITH confirmed_utxos as (
            SELECT o.account_id, o.hash, o.output_index, o.address, o.value
            FROM output o
              INNER JOIN transaction tx
                ON  o.account_id = tx.account_id
                AND o.hash       = tx.hash
                AND tx.block_hash IS NOT NULL
            WHERE o.account_id = $accountId
            AND   o.derivation IS NOT NULL

            EXCEPT

            SELECT i.account_id, i.output_hash, i.output_index, i.address, i.value
            FROM input i
              INNER JOIN transaction tx
                ON  i.account_id = tx.account_id
                AND i.hash       = tx.hash
                AND tx.block_hash IS NOT NULL
            WHERE i.account_id = $accountId
            AND   i.derivation IS NOT NULL
          )

          SELECT COALESCE(SUM(value), 0), COALESCE(COUNT(value), 0)
          FROM confirmed_utxos
      """
        .query[(BigInt, Int)]
        .unique

    val receivedAndSentQuery =
      sql"""SELECT
              COALESCE(SUM(CASE WHEN operation_type = 'receive' THEN amount ELSE 0 END), 0) as received,
              COALESCE(SUM(CASE WHEN operation_type = 'send'    THEN amount ELSE 0 END), 0) as sent
            FROM operation op
            INNER JOIN transaction tx
              ON  op.account_id = tx.account_id
              AND op.hash       = tx.hash
              AND tx.block_hash IS NOT NULL
            WHERE op.account_id = $accountId
         """
        .query[(BigInt, BigInt)]
        .unique

    for {
      result1 <- balanceAndUtxosQuery
      result2 <- receivedAndSentQuery
    } yield {
      val (balance, utxos) = result1
      val (received, sent) = result2
      BlockchainBalance(balance, utxos, received, sent, 0)
    }
  }

  def getUnconfirmedBalance(
      accountId: AccountUid
  ): ConnectionIO[BigInt] =
    sql"""
          WITH new_utxo as (SELECT COALESCE(SUM(o.value), 0) as value
          FROM output o
            INNER JOIN transaction tx
              ON  o.account_id = tx.account_id
              AND o.hash       = tx.hash
              AND tx.block_hash IS NULL -- take only new inputs account
          WHERE o.account_id = $accountId
            AND o.derivation IS NOT NULL
          ),

          used_utxos as (
            SELECT COALESCE(SUM(i.value), 0) as value
            FROM input i
              INNER JOIN transaction tx
                ON  i.account_id = tx.account_id
                AND i.hash       = tx.hash
                AND tx.block_hash IS NULL
            WHERE i.account_id = $accountId
              AND i.derivation IS NOT NULL
          )

          SELECT new_utxo.value - used_utxos.value as pending_amount
          FROM new_utxo, used_utxos
      """
      .query[BigInt]
      .unique
}
