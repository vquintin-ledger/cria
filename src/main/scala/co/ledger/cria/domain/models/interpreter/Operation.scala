package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.AccountId
import io.circe.{Decoder, Encoder}

import java.security.MessageDigest
import java.time.Instant

case class Operation(
    uid: Operation.UID,
    accountId: AccountId,
    hash: TxHash,
    transaction: TransactionView,
    operationType: OperationType,
    amount: BigInt,
    fees: BigInt,
    time: Instant,
    blockHeight: Option[Long]
)

object Operation {
  case class UID(hex: String) extends AnyVal

  object UID {
    implicit val encoder: Encoder[UID] = Encoder[String].contramap(_.hex)
    implicit val decoder: Decoder[UID] = Decoder[String].map(UID(_))
  }

  def uid(
      accountId: AccountId,
      txId: TxHash,
      operationType: OperationType,
      blockHeight: Option[Long]
  ): UID = {

    val libcoreType = operationType match {
      case OperationType.Send    => "SEND"
      case OperationType.Receive => "RECEIVE"
    }

    val blockHeightPrefix = blockHeight.getOrElse(0L)
    val txIdPrefix        = txId.asString.take(2).toList.map(_.toLong).mkString.toLong.toHexString

    // This prefix ensures to have a sequential uid
    val prefix = s"$blockHeightPrefix$txIdPrefix"

    val rawUid =
      s"uid:${accountId.value.toString.toLowerCase}+${txId.asString}+$libcoreType"

    val hex =
      MessageDigest
        .getInstance("SHA-256")
        .digest(rawUid.getBytes("UTF-8"))
        .map("%02x".format(_))
        .mkString

    UID(prefix ++ hex)
  }

}
