package co.ledger.cria.domain

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.services.{CursorStateService, ExplorerClient, KeychainClient, Synchronizer}
import co.ledger.cria.domain.services.interpreter.{InterpreterImpl, PersistenceFacade}

final class CriaModule(persistence: PersistenceFacade, keychainClient: KeychainClient, getExplorerClient: Coin => ExplorerClient)
                      (implicit timer: Timer[IO], cs: ContextShift[IO]) {

  private val interpreter = new InterpreterImpl(getExplorerClient, persistence)

  val cursorStateService: Coin => CursorStateService[IO] =
    c => CursorStateService(getExplorerClient(c), interpreter).getLastValidState(_, _, _)

  val synchronizer: Synchronizer = new Synchronizer(keychainClient, getExplorerClient, interpreter, cursorStateService)
}
