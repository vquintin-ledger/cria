package co.ledger.cria.domain.adapters.persistence.lama

import cats.implicits._
import cats.effect.IO
import co.ledger.cria.config.{Config, PersistenceConfig}
import co.ledger.cria.domain.services.interpreter.{PersistenceFacade, PersistenceFacadeTests}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import co.ledger.cria.domain.models.Generators._
import co.ledger.cria.domain.models.IOEq._
import co.ledger.cria.itutils.ContainerSpec
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class LamaPersistenceFacadeTest
    extends AnyFunSuite
    with FunSuiteDiscipline
    with ContainerSpec
    with BeforeAndAfter
    with ScalaCheckPropertyChecks {

  // Force lama only mode
  override def modifyConfig(config: Config): Config = {
    val lamaOnlyConfig = PersistenceConfig
      .fold[Option[PersistenceConfig.Lama]](
        _ => None,
        v => Some(PersistenceConfig.Lama(v)),
        (l, r, _) => l.orElse(r)
      )(config.persistence)
      .get
    config.copy(persistence = lamaOnlyConfig)
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

  checkAll("lama", PersistenceFacadeTests[IO](persistenceFacade).persistenceFacade)
}
