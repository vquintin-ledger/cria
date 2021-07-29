package co.ledger.cria.config

import co.ledger.cria.clients.explorer.models.ExplorerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class Config(
    explorer: ExplorerConfig,
    keychain: GrpcClientConfig,
    grpcServer: GrpcServerConfig,
    maxConcurrent: Int = 50, // TODO : bench [Runtime.getRuntime.availableProcessors() * x]
    db: PersistenceConfig
)

object Config {
  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
}

sealed abstract class DatabaseConfig {
  def url: String
  def userOpt: Option[String]
  def passwordOpt: Option[String]
  def driver: String
  val poolSize: Int = Runtime.getRuntime.availableProcessors() * 2
}

case class PostgresConfig(
    url: String,
    user: String,
    password: String
) extends DatabaseConfig {
  val driver: String = "org.postgresql.Driver"

  override def userOpt: Option[String] = Some(user)

  override def passwordOpt: Option[String] = Some(password)
}

object PostgresConfig {
  implicit val configReader: ConfigReader[PostgresConfig] = deriveReader[PostgresConfig]
}

case class SqliteConfig(url: String) extends DatabaseConfig {
  override def userOpt: Option[String] = None

  override def passwordOpt: Option[String] = None

  override def driver: String = "org.sqlite.JDBC"
}


class GrpcClientConfig(val host: String, val port: Int, val ssl: Boolean)

object GrpcClientConfig {
  implicit val configReader: ConfigReader[GrpcClientConfig] = deriveReader[GrpcClientConfig]
}

case class GrpcServerConfig(port: Int)

object GrpcServerConfig {
  implicit val configReader: ConfigReader[GrpcServerConfig] = deriveReader[GrpcServerConfig]
}
