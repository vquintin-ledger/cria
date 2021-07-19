package co.ledger.cria.config

import cats.Monad
import co.ledger.cria.domain.adapters.persistence.lama.LamaDb
import co.ledger.cria.domain.adapters.persistence.wd.WalletDaemonDb
import cats.implicits._
import co.ledger.cria.config.PersistenceConfig.Both
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

sealed abstract class PersistenceConfig extends Product with Serializable

object PersistenceConfig extends PersistenceConfigLowPriority {

  final case class WalletDaemon(walletDaemonDb: WalletDaemonDb) extends PersistenceConfig

  final case class Lama(lamaDb: LamaDb) extends PersistenceConfig

  final case class Both(primary: PersistenceConfig, secondary: PersistenceConfig) extends PersistenceConfig

  implicit lazy val configReader: ConfigReader[PersistenceConfig] =
    deriveReader[WalletDaemon]
      .orElse(deriveReader[Lama])
      .orElse(bothReader(configReader))

  def foldM[M[_], A](wd: WalletDaemonDb => M[A], lama: LamaDb => M[A], both: (A, A) => M[A])(c: PersistenceConfig)(implicit M: Monad[M]): M[A] =
    c match {
      case WalletDaemon(db) => wd(db)
      case Lama(db) => lama(db)
      case Both(c1, c2) =>
        for {
          a1 <- foldM(wd, lama, both)(c1)
          a2 <- foldM(wd, lama, both)(c2)
          res <- both(a1, a2)
        } yield res
    }

  def fold[A](wd: WalletDaemonDb => A, lama: LamaDb => A, both: (A, A) => A)(c: PersistenceConfig): A =
    c match {
      case WalletDaemon(db) => wd(db)
      case Lama(db) => lama(db)
      case Both(c1, c2) =>
          val a1 = fold(wd, lama, both)(c1)
          val a2 = fold(wd, lama, both)(c2)
          both(a1, a2)
    }
}

trait PersistenceConfigLowPriority {
  implicit def bothReader(implicit rReader: ConfigReader[PersistenceConfig]): ConfigReader[Both] = {
    val _ = rReader
    deriveReader[Both]
  }
}
