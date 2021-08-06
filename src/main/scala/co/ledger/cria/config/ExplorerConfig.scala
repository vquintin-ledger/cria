package co.ledger.cria.config

import cats.Monad
import co.ledger.cria.clients.explorer.v2
import co.ledger.cria.clients.explorer.v3
import cats.implicits._
import pureconfig.ConfigReader
import pureconfig.generic.FieldCoproductHint
import pureconfig.generic.semiauto.deriveReader

sealed trait ExplorerConfig

object ExplorerConfig {
  implicit val explorerConfigHint = new FieldCoproductHint[ExplorerConfig]("type") {
    override protected def fieldValue(name: String): String =
      name match {
        case "ExplorerV3" => "explorer-v3"
        case "ExplorerV2" => "explorer-v2"
      }
  }

  case class ExplorerV2(http: v2.models.ExplorerConfig, fallback: ExplorerConfig)
      extends ExplorerConfig

  object ExplorerV2 {
    implicit val configReaderExplorerV2: ConfigReader[ExplorerV2] = deriveReader[ExplorerV2]
  }

  case class ExplorerV3(http: v3.models.ExplorerConfig) extends ExplorerConfig

  object ExplorerV3 {
    implicit val configReaderExplorerV2: ConfigReader[ExplorerV3] = deriveReader[ExplorerV3]
  }

  implicit lazy val configReader: ConfigReader[ExplorerConfig] = deriveReader[ExplorerConfig]

  def foldM[M[_], A](
      fromV2: (v2.models.ExplorerConfig, A) => M[A],
      fromV3: v3.models.ExplorerConfig => M[A]
  )(conf: ExplorerConfig)(implicit monad: Monad[M]): M[A] =
    conf match {
      case ExplorerV2(http, fallback) =>
        foldM[M, A](fromV2, fromV3)(fallback).flatMap(a => fromV2(http, a))
      case ExplorerV3(http) => fromV3(http)
    }
}
