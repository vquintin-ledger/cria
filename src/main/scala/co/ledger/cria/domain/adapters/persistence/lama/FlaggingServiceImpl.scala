package co.ledger.cria.domain.adapters.persistence.lama

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import cats.implicits._
import co.ledger.cria.domain.adapters.persistence.lama.queries.OperationQueries
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import co.ledger.cria.domain.services.interpreter.FlaggingService
import doobie._
import doobie.implicits._

final class FlaggingServiceImpl(db: Transactor[IO])(implicit cs: ContextShift[IO])
    extends FlaggingService {

  def flagInputsAndOutputs(
      accountId: AccountUid,
      accountAddresses: List[AccountAddress]
  ): IO[Unit] = {
    val (internalAddresses, externalAddresses) =
      accountAddresses
        .partition(_.changeType == ChangeType.Internal)

    val flagInputs =
      NonEmptyList
        .fromList(accountAddresses)
        .map { addresses =>
          OperationQueries
            .flagBelongingInputs(
              accountId,
              addresses
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    // Flag outputs with known addresses and update address type (INTERNAL / EXTERNAL)
    val flagInternalOutputs =
      NonEmptyList
        .fromList(internalAddresses)
        .map { addresses =>
          OperationQueries
            .flagBelongingOutputs(
              accountId,
              addresses,
              ChangeType.Internal
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    val flagExternalOutputs =
      NonEmptyList
        .fromList(externalAddresses)
        .map { addresses =>
          OperationQueries
            .flagBelongingOutputs(
              accountId,
              addresses,
              ChangeType.External
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    (flagInputs, flagInternalOutputs, flagExternalOutputs).parTupled.void
  }

}
