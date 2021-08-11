package co.ledger.cria.domain.adapters.persistence.wd

import cats.effect.IO
import cats.implicits._
import co.ledger.cria.config.{Config, PersistenceConfig}
import co.ledger.cria.domain.models.Generators._
import co.ledger.cria.domain.models.IOEq._
import co.ledger.cria.domain.services.interpreter.{PersistenceFacade, PersistenceFacadeTests}
import co.ledger.cria.itutils.ContainerSpec
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class WDPersistenceFacadeTest
    extends AnyFunSuite
    with FunSuiteDiscipline
    with ContainerSpec
    with BeforeAndAfter
    with ScalaCheckPropertyChecks {

  // Force wd only mode
  override def modifyConfig(config: Config): Config = {
    val wdOnlyConfig = PersistenceConfig
      .fold[Option[PersistenceConfig.WalletDaemon]](
        v => Some(PersistenceConfig.WalletDaemon(v)),
        _ => None,
        (l, r, _) => l.orElse(r)
      )(config.persistence)
      .get
    config.copy(persistence = wdOnlyConfig)
  }

  private var persistenceFacade: PersistenceFacade[IO] = _

  private var deallocate: IO[Unit] = IO.unit

  before {
    val prgm =
      for {
        p <- testResources.allocated
        (tr, deallocate) = p
        _ <- tr.testUtils.migrate
      } yield (tr.clients.persistenceFacade, deallocate)
    val p = prgm.unsafeRunSync()
    persistenceFacade = p._1
    deallocate = p._2
  }

  after {
    deallocate.unsafeRunSync()
  }

  checkAll("wd", PersistenceFacadeTests[IO](persistenceFacade).persistenceFacade)
}
