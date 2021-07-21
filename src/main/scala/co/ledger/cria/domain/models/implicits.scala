package co.ledger.cria.domain.models

import java.time.Instant

import cats.data.NonEmptyList
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.{OperationType, TransactionView}
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.ChangeType
import doobie._
import doobie.postgres.implicits._

import scala.math.BigDecimal.javaBigDecimal2bigDecimal

object implicits {

  implicit def getNel[A](implicit ev: Get[List[A]]): Get[NonEmptyList[A]] =
    ev.map(NonEmptyList.fromList(_).getOrElse(sys.error("oops")))

  implicit def putNel[A](implicit ev: Put[List[A]]): Put[NonEmptyList[A]] =
    ev.contramap(_.toList)

  implicit val bigIntType: Meta[BigInt] =
    Meta.BigDecimalMeta.imap[BigInt](_.toBigInt)(BigDecimal(_).bigDecimal)

  implicit val operationTypeMeta: Meta[OperationType] =
    pgEnumStringOpt("operation_type", OperationType.fromKey, _.toString.toLowerCase())

  implicit val changeTypeMeta: Meta[ChangeType] =
    pgEnumStringOpt("change_type", ChangeType.fromKey, _.toString.toLowerCase())

  implicit val doobieMetaAccountUId: Meta[AccountUid] =
    Meta[String].timap[AccountUid](AccountUid)(_.value)

  implicit val doobieMetaWalletUid: Meta[WalletUid] =
    Meta[String].timap[WalletUid](WalletUid)(_.value)

  implicit lazy val readTransactionView: Read[TransactionView] =
    Read[
      (String, TxHash, Option[BlockHash], Option[Long], Option[Instant], Instant, Long, BigInt, Int)
    ].map {
      case (
            id,
            hash,
            blockHashO,
            blockHeightO,
            blockTimeO,
            receivedAt,
            lockTime,
            fees,
            confirmations
          ) =>
        TransactionView(
          id = id,
          hash = hash,
          receivedAt = receivedAt,
          lockTime = lockTime,
          fees = fees,
          inputs = Seq(),
          outputs = Seq(),
          block =
            blockHashO.map(blockHash => BlockView(blockHash, blockHeightO.get, blockTimeO.get)),
          confirmations = confirmations
        )
    }
}
