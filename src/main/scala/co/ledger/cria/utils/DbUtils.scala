package co.ledger.cria.utils

import cats.effect.IO
import co.ledger.cria.config.DatabaseConfig
import org.flywaydb.core.Flyway

object DbUtils {

  def flywayMigrate(conf: DatabaseConfig, path: String): IO[Unit] =
    IO(flyway(conf, path).migrate()).void

  def flyway(conf: DatabaseConfig, path: String) =
    Flyway
      .configure()
      .dataSource(conf.url, conf.userOpt.getOrElse(""), conf.passwordOpt.getOrElse(""))
      .locations(path)
      .load()

}
