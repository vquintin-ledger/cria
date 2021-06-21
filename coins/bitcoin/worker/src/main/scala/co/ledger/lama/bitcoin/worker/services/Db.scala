package co.ledger.lama.bitcoin.worker.services

import co.ledger.lama.common.utils.PostgresConfig
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

case class Db(batchConcurrency: Db.BatchConcurrency, postgres: PostgresConfig)

object Db {

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
}
