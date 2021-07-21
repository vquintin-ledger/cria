package co.ledger.cria.itutils

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.cria.domain.adapters.persistence.lama.LamaDb
import co.ledger.cria.domain.adapters.persistence.lama.queries.{
  LamaBalanceQueries,
  LamaOperationQueries
}
import co.ledger.cria.domain.adapters.persistence.lama.queries.LamaOperationQueries.{
  OpWithoutDetails,
  OperationDetails
}
import co.ledger.cria.itutils.models.{
  GetOperationsResult,
  GetUtxosResult,
  OperationPaginationState,
  PaginationCursor,
  PaginationToken
}
import co.ledger.cria.itutils.queries.LamaOperationTestQueries
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{CurrentBalance, Operation, TransactionView}
import co.ledger.cria.domain.models.{Sort, TxHash}
import co.ledger.cria.utils.{DbUtils, ResourceUtils}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.{Chunk, Pipe, Stream}
import org.flywaydb.core.Flyway

final class LamaTestUtils private (conf: LamaDb, db: Transactor[IO]) extends TestUtils {

  private val numberOfOperationsToBuildByQuery = 5

  def getOperations(
      accountId: AccountUid,
      limit: Int,
      sort: Sort,
      cursor: Option[PaginationToken[OperationPaginationState]]
  ): IO[GetOperationsResult] = {
    for {
      operations <-
        Stream
          .evalSeq(LamaOperationTestQueries.fetchOperations(accountId, limit, sort, cursor))
          .groupAdjacentBy(_.op.hash) // many operations by hash (RECEIVED AND SENT)
          .chunkN(numberOfOperationsToBuildByQuery)
          .flatMap { ops =>
            val txHashes = ops.map { case (txHash, _) => txHash }.toNel

            val inputsAndOutputs = Stream
              .emits(txHashes.toList)
              .flatMap(txHashes =>
                LamaOperationTestQueries
                  .fetchOperationDetails(accountId, sort, txHashes)
              )

            Stream
              .chunk(ops)
              .covary[ConnectionIO]
              .zip(inputsAndOutputs)
          }
          .transact(db)
          .through(makeOperation)
          .compile
          .toList

      total <- LamaOperationTestQueries
        .countOperations(accountId)
        .transact(db)

      // Build previous pagination by taking the first operation state.
      previousPagination <- operations.headOption
        .map { o =>
          LamaOperationTestQueries
            .hasPreviousPage(accountId, o.uid, sort)
            .transact(db)
            .map {
              case true =>
                Some(
                  PaginationToken(
                    OperationPaginationState(o.uid, o.blockHeight.getOrElse(0)),
                    isNext = false
                  )
                )
              case false => None
            }
        }
        .getOrElse(IO.pure(None))

      // Build next pagination by taking the last operation state
      nextPagination <- operations.lastOption
        .map { o =>
          LamaOperationTestQueries
            .hasNextPage(accountId, o.uid, sort)
            .transact(db)
            .map {
              case true =>
                Some(
                  PaginationToken(
                    OperationPaginationState(o.uid, o.blockHeight.getOrElse(0)),
                    isNext = true
                  )
                )
              case false => None
            }
        }
        .getOrElse(IO.pure(None))
    } yield {
      // Build the cursor pagination only if we have a previous or next pagination.
      val cursorPagination =
        Option
          .when(previousPagination.isDefined || nextPagination.isDefined) {
            PaginationCursor(
              previous = previousPagination.map(_.toBase64),
              next = nextPagination.map(_.toBase64)
            )
          }

      GetOperationsResult(operations, total, cursorPagination)
    }
  }

  private lazy val makeOperation: Pipe[
    IO,
    (
        (TxHash, Chunk[OpWithoutDetails]),
        LamaOperationQueries.OperationDetails
    ),
    Operation
  ] =
    _.flatMap {
      case (
            (txHash, sentAndReceivedOperations),
            inputsWithOutputsByTxHash
          ) =>
        Stream
          .chunk(sentAndReceivedOperations)
          .takeWhile(_ => txHash == inputsWithOutputsByTxHash.txHash)
          .map { op =>
            operation(op, inputsWithOutputsByTxHash)
          }
    }

  private def operation(
      emptyOperation: OpWithoutDetails,
      inputsWithOutputsByTxHash: OperationDetails
  ) =
    Operation(
      uid = emptyOperation.op.uid,
      accountId = emptyOperation.op.accountId,
      hash = emptyOperation.op.hash,
      transaction = TransactionView(
        id = emptyOperation.tx.id,
        hash = emptyOperation.tx.hash,
        receivedAt = emptyOperation.tx.receivedAt,
        lockTime = emptyOperation.tx.lockTime,
        fees = emptyOperation.tx.fees,
        inputs = inputsWithOutputsByTxHash.inputs,
        outputs = inputsWithOutputsByTxHash.outputs,
        block = emptyOperation.tx.block,
        confirmations = emptyOperation.tx.confirmations
      ),
      operationType = emptyOperation.op.operationType,
      amount = emptyOperation.op.amount,
      fees = emptyOperation.op.fees,
      time = emptyOperation.op.time,
      blockHeight = emptyOperation.op.blockHeight
    )

  def getUtxos(
      accountId: AccountUid,
      limit: Int,
      offset: Int,
      sort: Sort
  ): IO[GetUtxosResult] =
    for {
      utxos <- LamaOperationTestQueries
        .fetchConfirmedUTXOs(accountId, sort, Some(limit + 1), Some(offset))
        .transact(db)
        .compile
        .toList

      total <- LamaOperationTestQueries.countUTXOs(accountId).transact(db)

    } yield {
      // We get 1 more than necessary to know if there's more, then we return the correct number
      GetUtxosResult(utxos.slice(0, limit), total, truncated = utxos.size > limit)
    }

  def getBalance(accountId: AccountUid): IO[CurrentBalance] =
    (for {
      blockchainBalance <- LamaBalanceQueries.getBlockchainBalance(accountId)
      mempoolBalance    <- LamaBalanceQueries.getUnconfirmedBalance(accountId)
    } yield {
      CurrentBalance(
        blockchainBalance.balance,
        blockchainBalance.utxos,
        blockchainBalance.received,
        blockchainBalance.netSent + blockchainBalance.fees,
        mempoolBalance
      )
    }).transact(db)

  override def setupAccount(accountUid: AccountUid, walletUid: WalletUid): IO[Int] =
    IO.pure(1)

  override def getOperationCount(accountId: AccountUid): IO[Int] =
    getOperations(accountId, Int.MaxValue, Sort.Ascending, None).map(_.total)

  private lazy val flyway: Flyway = DbUtils.flyway(conf.postgres, "classpath:/db/lama_migration/")

  override def migrate: IO[Unit] = IO(flyway.migrate())

  override def clean: IO[Unit] = IO(flyway.clean())
}

object LamaTestUtils {
  def apply(conf: LamaDb)(implicit cs: ContextShift[IO], t: Timer[IO]): Resource[IO, TestUtils] =
    ResourceUtils
      .postgresTransactor(conf.postgres)
      .map(transactor => new LamaTestUtils(conf, transactor))
}
