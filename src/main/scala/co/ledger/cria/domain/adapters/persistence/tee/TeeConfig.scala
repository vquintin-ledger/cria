package co.ledger.cria.domain.adapters.persistence.tee

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class TeeConfig(concurrency: TeeConcurrency, onDiff: OnDiffAction)

object TeeConfig {
  implicit val configReader: ConfigReader[TeeConfig] = deriveReader[TeeConfig]
}

sealed trait TeeConcurrency

object TeeConcurrency {
  case object Parallel extends TeeConcurrency {
    implicit val configReader: ConfigReader[Parallel.type] = deriveReader
  }
  case object Sequential extends TeeConcurrency {
    implicit val configReader: ConfigReader[Sequential.type] = deriveReader
  }

  implicit val configReader: ConfigReader[TeeConcurrency] = deriveReader[TeeConcurrency]
}

sealed trait OnDiffAction

object OnDiffAction {
  case object Fail extends OnDiffAction {
    implicit val configReader: ConfigReader[Fail.type] = deriveReader
  }
  case object Log extends OnDiffAction {
    implicit val configReader: ConfigReader[Log.type] = deriveReader
  }

  implicit val configReader: ConfigReader[OnDiffAction] = deriveReader[OnDiffAction]
}
