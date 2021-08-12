package co.ledger.cria.domain.models

import cats.data.NonEmptyList
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.interpreter.OperationType
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
}
