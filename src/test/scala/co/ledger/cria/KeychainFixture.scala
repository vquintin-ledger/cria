package co.ledger.cria

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.cria.clients.explorer.ExplorerClient.Address
import co.ledger.cria.domain.models.keychain
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import co.ledger.cria.domain.services.KeychainClient

import scala.collection.mutable

object KeychainFixture {

  trait UsedAddressesTracker {
    val newlyMarkedAddresses: mutable.Map[Address, Address] = mutable.Map.empty
  }

  def keychainClient(
      addresses: LazyList[Address],
      lookaheadSize: Int = 20
  ): KeychainClient with UsedAddressesTracker =
    new KeychainClient with UsedAddressesTracker {

      override def getLookaheadSize(keychainId: KeychainId): IO[Int] = IO.pure(lookaheadSize)

      override def getAddresses(
          keychainId: KeychainId,
          fromIndex: Int,
          toIndex: Int,
          changeType: Option[ChangeType]
      ): IO[List[AccountAddress]] =
        IO.delay(
          addresses
            .slice(fromIndex, toIndex)
            .map(AccountAddress(_, ChangeType.External, derivation = NonEmptyList.one(1)))
            .toList
        )

      override def markAddressesAsUsed(
          keychainId: KeychainId,
          addresses: List[String]
      ): IO[Unit] = {
        addresses.foreach(a => newlyMarkedAddresses.update(a, a))
        IO.unit
      }

      override def getKnownAndNewAddresses(
          keychainId: KeychainId,
          changeType: Option[ChangeType]
      ): IO[List[AccountAddress]] =
        for {
          knownAddresses <- IO(
            newlyMarkedAddresses.keys.toList.map(
              keychain.AccountAddress(_, ChangeType.External, derivation = NonEmptyList.one(1))
            )
          )

          newAddresses <- getAddresses(
            keychainId,
            newlyMarkedAddresses.size - 1,
            newlyMarkedAddresses.size + lookaheadSize - 1
          )

        } yield knownAddresses ++ newAddresses
    }

}
