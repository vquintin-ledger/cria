package co.ledger.cria.domain.adapters.wd.queries

import cats.implicits._
import co.ledger.cria.domain.adapters.wd.models.{
  WDBlock,
  WDInput,
  WDOperation,
  WDOutput,
  WDTransaction
}
import co.ledger.cria.domain.models.implicits._
import co.ledger.cria.logging.DoobieLogHandler
import doobie._
import doobie.implicits._

object WDQueries extends DoobieLogHandler {

  def saveWDOperation(op: WDOperation): doobie.ConnectionIO[Int] = {

    val update =
      fr""" DO UPDATE SET
              block_uid = ${op.blockUid}
            WHERE operations.block_uid IS NULL
       """

    val noUpdate = fr""" DO NOTHING"""

    val operationQuery =
      sql"""INSERT INTO operations(
        uid,
        account_uid,
        wallet_uid,
        type,
        date,
        senders,
        recipients,
        amount,
        fees,
        block_uid,
        currency_name,
        trust
      ) VALUES (
        ${op.uid},
        ${op.accountUid},
        ${op.walletUid},
        ${op.operationType},
        ${op.date},
        ${op.senders},
        ${op.recipients},
        ${op.amount},
        ${op.fees},
        ${op.blockUid},
        ${op.currencyName},
        ${op.trust})
      ON CONFLICT ON CONSTRAINT operations_pkey
    """ ++
        op.blockUid.map(_ => update).getOrElse(noUpdate)

    val operationLinkQuery =
      sql"""INSERT INTO bitcoin_operations(uid, transaction_uid, transaction_hash) VALUES(${op.uid}, ${op.txUid}, ${op.txHash})
      ON CONFLICT DO NOTHING"""

    operationQuery.update.run.flatMap(_ => operationLinkQuery.update.run)
  }

  def saveTransaction(tx: WDTransaction): doobie.ConnectionIO[Int] = {
    val update =
      fr""" DO UPDATE SET
              block_uid = ${tx.blockUid}
            WHERE bitcoin_transactions.block_uid IS NULL
       """

    val noUpdate = fr""" DO NOTHING"""

    val txQuery =
      sql"""INSERT INTO bitcoin_transactions(
        transaction_uid,
        hash,
        version,
        block_uid,
        time,
        locktime
      ) VALUES (
      ${tx.uid}, ${tx.hash}, ${tx.version}, ${tx.blockUid}, ${tx.time}, ${tx.locktime})
      ON CONFLICT ON CONSTRAINT bitcoin_transactions_pkey """ ++
        tx.blockUid.map(_ => update).getOrElse(noUpdate)

    txQuery.update.run
  }

  def saveInputs(tx: WDTransaction, inputs: List[WDInput]): doobie.ConnectionIO[Int] = {
    val inputsQuery =
      """INSERT INTO bitcoin_inputs(
        uid,
        previous_output_idx,
        previous_tx_hash,
        previous_tx_uid,
        amount,
        address,
        coinbase,
        sequence
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT DO NOTHING
    """

    val links = inputs.map { i =>
      sql"""INSERT INTO bitcoin_transaction_inputs(
                transaction_uid,
                transaction_hash,
                input_uid,
                input_idx
              ) VALUES (${tx.uid}, ${tx.hash}, ${i.uid}, ${i.inputIndex})
              ON CONFLICT DO NOTHING
           """.update.run
    }

    Update[WDInput](inputsQuery).updateMany(inputs).flatMap(a => links.sequence.as(a))
  }

  def saveOutputs(
                   outputs: List[WDOutput],
                   txUid: String,
                   txHash: String
                 ): doobie.ConnectionIO[Int] = {
    val outputsQuery =
      s"""INSERT INTO bitcoin_outputs(
        idx,
        transaction_uid,
        transaction_hash,
        amount,
        script,
        address,
        account_uid,
        block_height,
        replaceable
      ) VALUES (?, '$txUid', '$txHash', ?, ?, ?, ?, ?, ?)
      ON CONFLICT DO NOTHING
    """

    Update[WDOutput](outputsQuery).updateMany(outputs)
  }

  def saveBlocks(blocks: List[WDBlock]): doobie.ConnectionIO[Int] = {
    val blocksQuery =
      """INSERT INTO blocks(
        uid,
        hash,
        height,
        time,
        currency_name
      ) VALUES (?, ?, ?, ?, ?)
      ON CONFLICT DO NOTHING
    """

    Update[WDBlock](blocksQuery).updateMany(blocks)
  }

  def deleteTransaction(hash: String): doobie.ConnectionIO[Int] = {
    sql"""DELETE FROM operations WHERE txHash = $hash AND date >= :date""".update.run *>
      sql"""DELETE FROM bitcoin_inputs WHERE uid IN (
      SELECT input_uid FROM bitcoin_transaction_inputs
      WHERE transaction_uid = $hash
      )
    """.update.run *>
      sql"""DELETE FROM bitcoin_transactions
    WHERE hash = $hash""".update.run
  }

  def deleteOperation(hash: String): doobie.ConnectionIO[Int] = {
    sql"""DELETE FROM operations WHERE txHash = $hash AND date >= :date""".update.run *>
      sql"""DELETE FROM bitcoin_inputs WHERE uid IN (
      SELECT input_uid FROM bitcoin_transaction_inputs
      WHERE transaction_uid = $hash
      )
    """.update.run *>
      sql"""DELETE FROM bitcoin_transactions
    WHERE hash = $hash""".update.run
  }

  def deleteBlocksFrom(blockHeight: Option[Long]): doobie.ConnectionIO[Int] =
    sql"""DELETE
          FROM blocks
          WHERE height >= $blockHeight
       """.update.run

}
