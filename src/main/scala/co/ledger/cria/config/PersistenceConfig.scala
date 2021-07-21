package co.ledger.cria.config

import cats.Monad
import co.ledger.cria.domain.adapters.persistence.lama.LamaDb
import co.ledger.cria.domain.adapters.persistence.wd.WalletDaemonDb
import cats.implicits._
import co.ledger.cria.config.PersistenceConfig.Both
import co.ledger.cria.domain.adapters.persistence.tee.TeeConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

sealed abstract class PersistenceConfig extends Product with Serializable

object PersistenceConfig extends PersistenceConfigLowPriority {

  final case class WalletDaemon(walletDaemonDb: WalletDaemonDb) extends PersistenceConfig

  final case class Lama(lamaDb: LamaDb) extends PersistenceConfig

  final case class Both(primary: PersistenceConfig, secondary: PersistenceConfig, tee: TeeConfig)
      extends PersistenceConfig

  implicit lazy val configReader: ConfigReader[PersistenceConfig] =
    deriveReader[WalletDaemon]
      .orElse(deriveReader[Lama])
      .orElse(bothReader(configReader))

  def foldM[M[_], A](
      wd: WalletDaemonDb => M[A],
      lama: LamaDb => M[A],
      both: (A, A, TeeConfig) => M[A]
  )(c: PersistenceConfig)(implicit M: Monad[M]): M[A] =
    c match {
      case WalletDaemon(db) => wd(db)
      case Lama(db)         => lama(db)
      case Both(c1, c2, tee) =>
        for {
          a1  <- foldM(wd, lama, both)(c1)
          a2  <- foldM(wd, lama, both)(c2)
          res <- both(a1, a2, tee)
        } yield res
    }

  def fold[A](wd: WalletDaemonDb => A, lama: LamaDb => A, both: (A, A, TeeConfig) => A)(
      c: PersistenceConfig
  ): A =
    c match {
      case WalletDaemon(db) => wd(db)
      case Lama(db)         => lama(db)
      case Both(c1, c2, tee) =>
        val a1 = fold(wd, lama, both)(c1)
        val a2 = fold(wd, lama, both)(c2)
        both(a1, a2, tee)
    }
}

trait PersistenceConfigLowPriority {
  implicit def bothReader(implicit rReader: ConfigReader[PersistenceConfig]): ConfigReader[Both] = {
    val _ = rReader
    deriveReader[Both]
  }
}
