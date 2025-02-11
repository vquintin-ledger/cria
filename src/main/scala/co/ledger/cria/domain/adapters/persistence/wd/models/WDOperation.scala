package co.ledger.cria.domain.adapters.persistence.wd.models

import java.math.BigInteger
import java.security.MessageDigest
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{Coin, Operation, OperationType, OutputView}
import doobie.Write

case class WDOperation(
    uid: String,
    accountUid: String,
    walletUid: String,
    operationType: String,
    date: String,
    senders: String,
    recipients: String,
    amount: String,
    fees: String,
    blockUid: Option[String],
    currencyName: String,
    trust: String,
    txUid: String,
    txHash: String
)

object WDOperation {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromOperation(
      accountUid: AccountUid,
      operation: Operation,
      coin: Coin,
      walletUid: WalletUid
  ): WDOperation = {
    val view = operation.transaction
    val tx   = WDTransaction.fromTransactionView(accountUid, view, coin)
    WDOperation(
      uid = computeUid(
        operation.accountId,
        operation.hash,
        operation.operationType
      ),
      accountUid = operation.accountId.value,
      walletUid = walletUid.value,
      operationType = operation.operationType.name.toUpperCase,
      date = operation.time.toString,
      senders = tx.inputs.map(_.address).distinct.mkString(","),
      recipients = getRecipients(operation.operationType, view.outputs).mkString(","),
      amount = toHexString(netAmount(operation.operationType, operation.amount, operation.fees)),
      fees = toHexString(operation.fees),
      blockUid = tx.blockUid,
      currencyName = coin.name,
      trust = "",
      txUid = tx.uid,
      txHash = operation.hash.asString
    )
  }

  private[models] def getRecipients(
      operationType: OperationType,
      outputs: Seq[OutputView]
  ): Seq[String] = {
    operationType match {
      case OperationType.Send =>
        outputs.collect {
          case o if o.derivation.isEmpty => o.address
        }.distinct
      case OperationType.Receive => outputs.map(_.address).distinct
    }
  }

  private[models] def netAmount(
      operationType: OperationType,
      value: BigInt,
      fees: BigInt
  ): BigInt = {
    operationType match {
      case OperationType.Receive => value
      case OperationType.Send    => value - fees
    }
  }

  private[models] def toHexString(amount: BigInt): String = {
    val amountStr = amount.toString(16)
    if (amountStr.size % 2 == 1) "0" + amountStr
    else amountStr
  }

  def computeUid(accountUid: AccountUid, txHash: TxHash, operationType: OperationType): String = {
    String.format(
      "%064x",
      new BigInteger(
        1,
        digester.digest(
          s"uid:${accountUid.value}+${txHash.asString}+${operationType.name.toUpperCase}"
            .getBytes("UTF-8")
        )
      )
    )
  }

  implicit lazy val writeWDOperation: Write[WDOperation] =
    Write[
      (
          String,
          String,
          String,
          String,
          String,
          String,
          String,
          String,
          String,
          Option[String],
          String,
          String
      )
    ].contramap { op =>
      (
        op.uid,
        op.accountUid,
        op.walletUid,
        op.operationType,
        op.date,
        op.senders,
        op.recipients,
        op.amount,
        op.fees,
        op.blockUid,
        op.currencyName,
        op.trust
      )
    }
}
