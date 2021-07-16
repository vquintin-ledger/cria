package co.ledger.cria.domain.services.interpreter

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
            SELECT o.account_uid, o.hash, o.output_index, o.address, o.value
            FROM output o
              INNER JOIN transaction tx
                ON  o.account_uid = tx.account_uid
                AND o.hash       = tx.hash
                AND tx.block_hash IS NOT NULL
            WHERE o.account_uid = $accountId
            AND   o.derivation IS NOT NULL
            
            EXCEPT
            
            SELECT i.account_uid, i.output_hash, i.output_index, i.address, i.value
            FROM input i
              INNER JOIN transaction tx
                ON  i.account_uid = tx.account_uid
                AND i.hash       = tx.hash
                AND tx.block_hash IS NOT NULL
            WHERE i.account_uid = $accountId
            AND   i.derivation IS NOT NULL
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
          WITH new_utxo as (SELECT COALESCE(SUM(o.value), 0) as value
          FROM output o
            INNER JOIN transaction tx
              ON  o.account_uid = tx.account_uid
              AND o.hash       = tx.hash
              AND tx.block_hash IS NULL -- take only new inputs account
          WHERE o.account_uid = $accountId
            AND o.derivation IS NOT NULL
          ),
          
          used_utxos as (
            SELECT COALESCE(SUM(i.value), 0) as value
            FROM input i
              INNER JOIN transaction tx
                ON  i.account_uid = tx.account_uid
                AND i.hash       = tx.hash
                AND tx.block_hash IS NULL
            WHERE i.account_uid = $accountId
              AND i.derivation IS NOT NULL              
          )
          
          SELECT new_utxo.value - used_utxos.value as pending_amount
          FROM new_utxo, used_utxos
      """
      .query[BigInt]
      .unique
}
