package co.ledger.cria.domain.adapters.wd.models

import java.math.BigInteger
import java.security.MessageDigest
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{Coin, TransactionView}
import doobie.Write

case class WDTransaction(
    uid: String,
    hash: String,
    version: Int,
    blockUid: Option[String],
    time: String,
    locktime: Int,
    inputs: List[WDInput],
    outputs: List[WDOutput]
)

object WDTransaction {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromTransactionView(
      accountId: AccountUid,
      tx: TransactionView,
      coin: Coin
  ): WDTransaction = {
    val block = tx.block.map(WDBlock.fromBlock(_, coin))
    val accountIdString = accountId.value
    WDTransaction(
      uid = createUid(accountIdString, tx.hash.asString),
      hash = tx.hash.asString,
      version = 1,
      blockUid = block.map(_.uid),
      tx.receivedAt.toString,
      tx.lockTime.toInt,
      tx.inputs.toList.map(WDInput.fromInput(_, accountIdString)),
      tx.outputs.toList.map(WDOutput.fromOutput(_, accountIdString, block))
    )
  }

  def createUid(accountUid: String, txHash: String) = {
    String.format(
      "%064x",
      new BigInteger(
        1,
        digester.digest(s"uid:$accountUid+$txHash".getBytes("UTF-8"))
      )
    )
  }

  implicit lazy val writeWDTransaction: Write[WDTransaction] =
    Write[
      (
        String,
          String,
          Int,
          Option[String],
          String,
          Int
        )
    ].contramap { tx =>
      (
        tx.uid,
        tx.hash,
        tx.version,
        tx.blockUid,
        tx.time,
        tx.locktime
      )
    }
}
