package co.ledger.cria.domain.adapters.persistence.wd

import co.ledger.cria.config.PostgresConfig
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto.deriveReader

case class WalletDaemonDb(
    batchConcurrency: WalletDaemonDb.BatchConcurrency,
    walletDaemon: PostgresConfig,
    criaExtra: PostgresConfig
)

object WalletDaemonDb {

  case class BatchConcurrency(value: Int) extends AnyVal
  object BatchConcurrency {
    implicit val configReader: ConfigReader[BatchConcurrency] =
      ConfigReader.fromCursor[BatchConcurrency] { cur =>
        cur.asInt.flatMap {
          case i if i > 0 => Right(BatchConcurrency(i))
          case i =>
            cur.failed(CannotConvert(i.toString, "BatchConcurrency", s"$i is not positive"))

        }
      }
  }

  implicit val dbConfigReader: ConfigReader[WalletDaemonDb] = deriveReader[WalletDaemonDb]
}
