package co.ledger.cria.domain.models

import co.ledger.cria.domain.models.interpreter.Satoshis
import cats.implicits._

sealed trait TransactionType

object TransactionType {
  def fromAmounts(input: Satoshis, output: Satoshis, change: Satoshis): TransactionType = {
    (input > Satoshis.zero, output > Satoshis.zero) match {
      case (true, false)               => SendType
      case (false, true)               => ReceiveType
      case (true, true)                => BothType
      case _ if change > Satoshis.zero => ChangeOnlyType
      case _                           => NoneType
    }
  }
}

case object SendType       extends TransactionType
case object ReceiveType    extends TransactionType
case object BothType       extends TransactionType
case object ChangeOnlyType extends TransactionType
case object NoneType       extends TransactionType
