package co.ledger.cria.utils

import cats.effect.IO
import co.ledger.cria.config.PostgresConfig
import org.flywaydb.core.Flyway

object DbUtils {

  def flywayMigrate(conf: PostgresConfig, path: String = "classpath:/db/migration/"): IO[Unit] =
    IO(flyway(conf, path).migrate()).void

  def flyway(conf: PostgresConfig, path: String = "classpath:/db/migration/") =
    Flyway
      .configure()
      .dataSource(conf.url, conf.user, conf.password)
      .locations(path)
      .load()

}
