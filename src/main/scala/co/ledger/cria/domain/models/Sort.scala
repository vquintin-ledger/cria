package co.ledger.cria.domain.models

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

sealed trait Sort {
  val name: String
  override def toString: String = name
}

object Sort {
  final case object Ascending extends Sort {
    val name: String = "ASC"
  }

  final case object Descending extends Sort {
    val name: String = "DESC"
  }

  val all: Map[String, Sort] = Map(Ascending.name -> Ascending, Descending.name -> Descending)

  def fromKey(key: String): Option[Sort] = all.get(key)

  implicit val configReader: ConfigReader[Sort] =
    ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Sort", "unknown")))

  def fromIsAsc(isAsc: Boolean): Sort =
    if (isAsc) Ascending
    else Descending
}
