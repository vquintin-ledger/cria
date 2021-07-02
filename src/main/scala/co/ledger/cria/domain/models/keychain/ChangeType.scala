package co.ledger.cria.domain.models.keychain

import io.circe.{Decoder, Encoder}

sealed trait ChangeType {
  val name: String
}

object ChangeType {

  case object Internal extends ChangeType {
    val name = "internal"
  }

  case object External extends ChangeType {
    val name = "external"
  }

  implicit val encoder: Encoder[ChangeType] = Encoder.encodeString.contramap(_.name)
  implicit val decoder: Decoder[ChangeType] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as change type"))

  val all: Map[String, ChangeType] = Map(Internal.name -> Internal, External.name -> External)

  def fromKey(key: String): Option[ChangeType] = all.get(key)
}
