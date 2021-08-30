package co.ledger.cria.domain.adapters.persistence.lama.queries

import cats.data.NonEmptyList
import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{
  BlockHash,
  BlockView,
  InputView,
  OutputView,
  Satoshis,
  TransactionView
}
import co.ledger.cria.domain.models.{Sort, TxHash}
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import co.ledger.cria.domain.models.implicits._
import LamaQueryImplicits._
import fs2._

import java.time.Instant

object LamaTransactionQueries extends DoobieLogHandler {

  case class TransactionDetails(
      txHash: TxHash,
      inputs: List[InputView],
      outputs: List[OutputView]
  )

  case class TransactionRow(
      accountId: String,
      id: String,
      hash: TxHash,
      blockHash: Option[BlockHash],
      blockHeight: Option[Long],
      blockTime: Option[Instant],
      receivedAt: Instant,
      lockTime: Long,
      fees: Satoshis,
      confirmations: Int
  )

  def fetchMostRecentBlocks(accountId: AccountUid): Stream[ConnectionIO, BlockView] = {
    sql"""SELECT DISTINCT block_hash, block_height, time
          FROM operation
          WHERE account_id = $accountId
          ORDER BY block_height DESC
          LIMIT 200 -- the biggest reorg that happened on bitcoin was 53 blocks long
       """.query[BlockView].stream
  }

  def fetchTransactions(
      accountUid: AccountUid,
      sort: Sort,
      hashes: NonEmptyList[TxHash]
  ): Stream[ConnectionIO, TransactionRow] =
    (sql"""SELECT account_id, id, hash, block_hash, block_height, block_time, received_at, lock_time, fees, confirmations
          FROM transaction
          WHERE account_id = $accountUid
            AND
       """ ++ Fragments.in(fr"hash", hashes)
      ++ Fragment.const(s"ORDER BY block_time $sort, hash $sort"))
      .query[TransactionRow]
      .stream

  def saveTransaction(accountId: AccountUid, tx: TransactionView): ConnectionIO[Int] =
    for {
      txStatement <- insertTx(accountId, tx)

      _ <- insertInputs(
        accountId,
        tx.hash,
        tx.inputs.toList
      )

      _ <- insertOutputs(accountId, tx.hash, tx.outputs.toList)
    } yield txStatement

  def deleteUnconfirmedTransactions(accountId: AccountUid): doobie.ConnectionIO[Int] = {
    sql"""DELETE FROM transaction
         WHERE account_id = $accountId
         AND block_hash IS NULL
       """.update.run
  }

  def deleteRejectedTransaction(
      accountId: AccountUid,
      hash: TxHash
  ): doobie.ConnectionIO[Int] = {
    sql"""DELETE FROM transaction
         WHERE account_id = $accountId
         AND block_hash IS NULL
         AND hash = $hash
       """.query[Int].unique
  }

  private def insertTx(
      accountId: AccountUid,
      tx: TransactionView
  ): doobie.ConnectionIO[Int] = {

    //TODO: check if we should remove the "WHERE" clause to allow transctions to fall back in mempool (after reorg)
    val update =
      fr"""DO UPDATE SET
              block_hash   = ${tx.block.map(_.hash)},
              block_height = ${tx.block.map(_.height)},
              block_time   = ${tx.block.map(_.time)}
            WHERE transaction.block_hash IS NULL
       """

    val noUpdate = fr"""DO NOTHING"""

    val query = sql"""INSERT INTO transaction (
            account_id, id, hash, block_hash, block_height, block_time, received_at, lock_time, fees, confirmations
          ) VALUES (
            $accountId,
            ${tx.id},
            ${tx.hash},
            ${tx.block.map(_.hash)},
            ${tx.block.map(_.height)},
            ${tx.block.map(_.time)},
            ${tx.receivedAt},
            ${tx.lockTime},
            ${tx.fees},
            ${tx.confirmations}
          ) ON CONFLICT ON CONSTRAINT transaction_pkey """ ++
      tx.block.map(_ => update).getOrElse(noUpdate)

    query.update.run
  }

  private def insertInputs(
      accountId: AccountUid,
      txHash: TxHash,
      inputs: List[InputView]
  ): doobie.ConnectionIO[Int] = {
    val query =
      s"""INSERT INTO input (
            account_id, hash, output_hash, output_index, input_index, value, address, script_signature, txinwitness, sequence, derivation
          ) VALUES (
            '${accountId.value}', '${txHash.asString}', ?, ?, ?, ?, ?, ?, ?, ?, ?
          )
          ON CONFLICT ON CONSTRAINT input_pkey DO NOTHING
       """
    Update[InputView](query).updateMany(inputs)
  }

  private def insertOutputs(
      accountId: AccountUid,
      txHash: TxHash,
      outputs: List[OutputView]
  ) = {
    val query = s"""INSERT INTO output (
            account_id, hash, output_index, value, address, script_hex, change_type, derivation
          ) VALUES (
            '${accountId.value}', '${txHash.asString}', ?, ?, ?, ?, ?, ?
          ) ON CONFLICT ON CONSTRAINT output_pkey DO NOTHING
        """
    Update[OutputView](query).updateMany(outputs)
  }

  def removeFromCursor(accountId: AccountUid, blockHeight: Long): ConnectionIO[Int] =
    sql"""DELETE from transaction
          WHERE account_id = $accountId
          AND block_height >= $blockHeight
       """.update.run

  def fetchTransactionDetails(
      accountId: AccountUid,
      sort: Sort,
      txHashes: NonEmptyList[TxHash]
  ): Stream[doobie.ConnectionIO, TransactionDetails] = {
    log.logger.debug(
      s"Fetching inputs and outputs for accountId $accountId and hashes in $txHashes"
    )

    def groupByTxHash[T]: Pipe[ConnectionIO, (TxHash, T), (TxHash, Chunk[T])] =
      _.groupAdjacentBy { case (txHash, _) => txHash }
        .map { case (txHash, chunks) => txHash -> chunks.map(_._2) }

    val inputs                                    = fetchInputs(accountId, sort, txHashes).stream.through(groupByTxHash)
    val outputs                                   = fetchOutputs(accountId, sort, txHashes).stream.through(groupByTxHash)
    implicit val txHashOrdering: Ordering[TxHash] = TxHash.orderTxHash.toOrdering

    inputs
      .zip(outputs)
      .collect {
        case ((txhash1, i), (txHash2, o)) if txhash1 == txHash2 =>
          TransactionDetails(
            txhash1,
            inputs = i.toList.flatten.sortBy(i => (i.outputHash, i.outputIndex)),
            outputs = o.toList.flatten.sortBy(_.outputIndex)
          )
      }
  }

  private def transactionOrder(sort: Sort) =
    Fragment.const(s"ORDER BY t.block_time $sort, t.hash $sort")

  private def allTxHashes(hashes: NonEmptyList[TxHash]) =
    Fragments.in(fr"t.hash", hashes.map(_.asString))

  private def fetchInputs(
      accountId: AccountUid,
      sort: Sort,
      txHashes: NonEmptyList[TxHash]
  ) = {

    val belongsToTxs = allTxHashes(txHashes)

    (sql"""
          SELECT t.hash, i.output_hash, i.output_index, i.input_index, i.value, i.address, i.script_signature, i.txinwitness, i.sequence, i.derivation
            FROM transaction t
            LEFT JOIN input i on i.account_id = t.account_id and i.hash = t.hash
           WHERE t.account_id = $accountId
             AND $belongsToTxs
       """ ++ transactionOrder(sort))
      .query[(TxHash, Option[InputView])]
  }

  private def fetchOutputs(
      accountId: AccountUid,
      sort: Sort,
      txHashes: NonEmptyList[TxHash]
  ) = {

    val belongsToTxs = allTxHashes(txHashes)

    (
      sql"""
          SELECT t.hash, output.output_index, output.value, output.address, output.script_hex, output.change_type, output.derivation
            FROM transaction t
            LEFT JOIN output on output.account_id = t.account_id and output.hash = t.hash
           WHERE t.account_id = $accountId
             AND $belongsToTxs
       """ ++ transactionOrder(sort)
    ).query[(TxHash, Option[OutputView])]
  }
}
