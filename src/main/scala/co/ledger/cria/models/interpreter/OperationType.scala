package co.ledger.cria.models.interpreter

import io.circe.{Decoder, Encoder}

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

  implicit val encoder: Encoder[OperationType] = Encoder.encodeString.contramap(_.name)
  implicit val decoder: Decoder[OperationType] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as operation type"))

  val all: Map[String, OperationType] = Map(Send.name -> Send, Receive.name -> Receive)

  def fromKey(key: String): Option[OperationType] = all.get(key)

}
