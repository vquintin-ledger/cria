package co.ledger.cria.domain.models.interpreter

sealed trait OperationType {
  val name: String
}

object OperationType {

  case object Send extends OperationType {
    val name = "send"
  }

  case object Receive extends OperationType {
    val name = "receive"
  }

  val all: Map[String, OperationType] = Map(Send.name -> Send, Receive.name -> Receive)

  def fromKey(key: String): Option[OperationType] = all.get(key)

}
