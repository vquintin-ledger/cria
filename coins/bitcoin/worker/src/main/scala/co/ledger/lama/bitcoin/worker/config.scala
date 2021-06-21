package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.config.ExplorerConfig
import co.ledger.lama.bitcoin.worker.services.Db
import co.ledger.lama.common.utils.{GrpcClientConfig, GrpcServerConfig}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._

object config {

  case class Config(
      explorer: ExplorerConfig,
      keychain: GrpcClientConfig,
      grpcServer: GrpcServerConfig,
      maxConcurrent: Int = 50, // TODO : bench [Runtime.getRuntime.availableProcessors() * x]
      db: Db
  )

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
  }

}
