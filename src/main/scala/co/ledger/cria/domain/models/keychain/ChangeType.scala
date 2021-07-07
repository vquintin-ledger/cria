package co.ledger.cria.domain.models.keychain

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

  val all: Map[String, ChangeType] = Map(Internal.name -> Internal, External.name -> External)

  def fromKey(key: String): Option[ChangeType] = all.get(key)
}
