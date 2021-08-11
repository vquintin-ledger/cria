package co.ledger.cria.domain

import cats.effect.{IO, Timer}
import co.ledger.cria.domain.models.interpreter.Coin
import co.ledger.cria.domain.services.explorer.ExplorerClient
import co.ledger.cria.domain.services.{CursorStateService, Synchronizer}
import co.ledger.cria.domain.services.interpreter.{InterpreterImpl, PersistenceFacade}
import co.ledger.cria.domain.services.keychain.KeychainClient

final class CriaModule(
    persistence: PersistenceFacade[IO],
    keychainClient: KeychainClient,
    getExplorerClient: Coin => ExplorerClient
)(implicit timer: Timer[IO]) {

  private val interpreter = new InterpreterImpl(getExplorerClient, persistence)

  private val cursorStateService: Coin => CursorStateService[IO] =
    c => CursorStateService(getExplorerClient(c), interpreter).getLastValidState(_, _, _)

  val synchronizer: Synchronizer =
    new Synchronizer(keychainClient, getExplorerClient, interpreter, cursorStateService)
}
