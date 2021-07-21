package co.ledger.cria.domain.mocks

import java.time.Instant

import cats.effect.IO
import co.ledger.cria.domain.models.account.{Account, AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import co.ledger.cria.domain.services.interpreter.Interpreter
import co.ledger.cria.logging.CriaLogContext
import fs2._

import scala.collection.mutable

class InterpreterClientMock extends Interpreter {

  var savedTransactions: mutable.Map[AccountUid, List[TransactionView]] = mutable.Map.empty
  var savedUnconfirmedTransactions: mutable.ArrayDeque[(AccountUid, List[TransactionView])] =
    mutable.ArrayDeque.empty
  var transactions: mutable.Map[AccountUid, List[TransactionView]] = mutable.Map.empty
  var operations: mutable.Map[AccountUid, List[Operation]]         = mutable.Map.empty

  def saveUnconfirmedTransactions(accountId: AccountUid, txs: List[TransactionView]): IO[Int] =
    IO.pure {
      savedUnconfirmedTransactions += accountId -> txs
      txs.size
    }

  def saveTransactions(
      accountId: AccountUid
  )(implicit lc: CriaLogContext): Pipe[IO, TransactionView, Unit] =
    _.chunks.evalMap { chunk =>
      val txs = chunk.toList

      savedTransactions.update(
        accountId,
        txs.filter(_.block.isDefined) ::: savedTransactions.getOrElse(accountId, Nil)
      )

      val unconfirmed = txs.filterNot(_.block.isDefined)
      if (unconfirmed.nonEmpty)
        saveUnconfirmedTransactions(accountId, unconfirmed)

      IO.unit
    }

  def getSavedTransaction(accountId: AccountUid): List[TransactionView] = savedTransactions
    .getOrElse(
      accountId,
      List.empty
    )
    .distinctBy(_.id)

  //TODO: fix
  def removeDataFromCursor(
      accountId: AccountUid,
      blockHeight: Long
  )(implicit lc: CriaLogContext): IO[Int] = {
    savedTransactions.update(
      accountId,
      savedTransactions(accountId)
        .filter(tx => tx.block.map(_.height).getOrElse(0L) < blockHeight)
    )

    transactions.update(
      accountId,
      transactions(accountId)
        .filter(tx => tx.block.exists(_.height < blockHeight))
    )

    operations.update(
      accountId,
      operations(accountId)
        .filter(op => op.transaction.block.exists(_.height < blockHeight))
    )

    IO.pure(0)
  }

  def getLastBlocks(accountId: AccountUid)(implicit lc: CriaLogContext): IO[List[BlockView]] = {
    val lastBlocks: List[BlockView] = savedTransactions(accountId)
      .collect { case TransactionView(_, _, _, _, _, _, _, Some(block), _) =>
        BlockView(
          block.hash,
          block.height,
          block.time
        )
      }
      .distinct
      .sortBy(_.height)(Ordering[Long].reverse)

    IO(lastBlocks)
  }

  def compute(account: Account, walletUid: WalletUid)(
      addresses: List[AccountAddress]
  )(implicit lc: CriaLogContext): IO[Int] = {

    val txViews = savedTransactions
      .getOrElse(account.accountUid, List.empty)

    val computedTxViews = txViews.map { tx =>
      tx.copy(
        outputs = tx.outputs.map { o =>
          val addressO = addresses.find(_.accountAddress == o.address)
          o.copy(
            changeType = addressO.map(_.changeType),
            derivation = addressO.map(_.derivation)
          )
        },
        inputs = tx.inputs.map { i =>
          i.copy(
            derivation = addresses.find(_.accountAddress == i.address).map(_.derivation)
          )
        }
      )
    }

    transactions.update(
      account.accountUid,
      computedTxViews
    )

    val operationToSave = transactions(account.accountUid).flatMap { tx =>
      val inputAmount =
        tx.inputs.filter(i => addresses.exists(_.accountAddress == i.address)).map(_.value).sum
      val outputAmount = tx.outputs
        .filter(o => o.belongs && o.changeType.contains(ChangeType.External))
        .map(_.value)
        .sum
      val changeAmount = tx.outputs
        .filter(o => o.belongs && o.changeType.contains(ChangeType.Internal))
        .map(_.value)
        .sum

      (inputAmount > 0, outputAmount > 0) match {
        // only input, consider changeAmount as deducted from spent
        case (true, false) =>
          List(
            makeOperation(account.accountUid, tx, inputAmount - changeAmount, OperationType.Send)
          )
        // only output, consider changeAmount as received
        case (false, true) =>
          List(
            makeOperation(
              account.accountUid,
              tx,
              outputAmount + changeAmount,
              OperationType.Receive
            )
          )
        // both input and output, consider change as deducted from spend
        case (true, true) =>
          List(
            makeOperation(account.accountUid, tx, inputAmount - changeAmount, OperationType.Send),
            makeOperation(account.accountUid, tx, outputAmount, OperationType.Receive)
          )
        case _ => Nil
      }

    }

    operations.update(
      account.accountUid,
      operationToSave
    )

    IO(operationToSave.size)
  }

  private def makeOperation(
      accountId: AccountUid,
      tx: TransactionView,
      amount: BigInt,
      operationType: OperationType
  ) = {
    Operation(
      Operation
        .uid(
          accountId,
          tx.hash,
          operationType,
          tx.block.map(_.height)
        ),
      accountId,
      tx.hash,
      tx,
      operationType,
      amount,
      tx.fees,
      tx.block.map(_.time).getOrElse(Instant.now()),
      tx.block.map(_.height)
    )
  }

}
