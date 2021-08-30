package co.ledger.cria.domain.mocks

import java.time.Instant
import cats.effect.IO
import cats.implicits.toFoldableOps
import co.ledger.cria.domain.models.account.{Account, AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import co.ledger.cria.domain.services.interpreter.Interpreter
import co.ledger.cria.logging.CriaLogContext
import fs2._

import scala.collection.mutable
import scala.math.Ordering.Implicits.infixOrderingOps

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

  //TODO: fix?
  def removeDataFromCursor(
      accountId: AccountUid,
      blockHeight: BlockHeight
  )(implicit lc: CriaLogContext): IO[Int] = {
    savedTransactions.update(
      accountId,
      savedTransactions(accountId)
        .filter(tx => tx.block.map(_.height).getOrElse(BlockHeight.genesis) < blockHeight)
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
    val lastBlocks: List[BlockView] = savedTransactions.get(accountId) match {
      case Some(blocks) =>
        blocks
          .collect { case TransactionView(_, _, _, _, _, _, _, Some(block), _) =>
            BlockView(
              block.hash,
              block.height,
              block.time
            )
          }
          .distinct
          .sortBy(_.height)(Ordering[BlockHeight].reverse)
      case None => Nil
    }

    IO(lastBlocks)
  }

  def getLastBlockHash(
      accountId: AccountUid
  )(implicit lc: CriaLogContext): IO[Option[BlockHash]] = {
    getLastBlocks(accountId).map(_.headOption.map(_.hash))
  }

  def compute(account: Account, walletUid: WalletUid, fromBlockHeight: Option[BlockHeight])(
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
        tx.inputs.filter(i => addresses.exists(_.accountAddress == i.address)).foldMap(_.value)
      val outputAmount = tx.outputs
        .filter(o => o.belongs && o.changeType.contains(ChangeType.External))
        .foldMap(_.value)
      val changeAmount = tx.outputs
        .filter(o => o.belongs && o.changeType.contains(ChangeType.Internal))
        .foldMap(_.value)

      List(
        makeOperation(
          account.accountUid,
          tx,
          (inputAmount - changeAmount).get,
          OperationType.Send
        ),
        makeOperation(account.accountUid, tx, outputAmount, OperationType.Receive)
      )
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
      amount: Satoshis,
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
