package co.ledger.cria

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.cria.clients.grpc.KeychainClient
import co.ledger.cria.clients.http.ExplorerClient.Address
import co.ledger.cria.models.interpreter.{AccountAddress, ChangeType}
import co.ledger.cria.models.keychain.{
  AccountKey,
  BitcoinLikeNetwork,
  BitcoinNetwork,
  KeychainId,
  KeychainInfo
}

import java.util.UUID
import co.ledger.cria.models.account.Scheme

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

      override def create(
          accountKey: AccountKey,
          scheme: Scheme,
          lookaheadSize: Int,
          network: BitcoinLikeNetwork
      ): IO[KeychainInfo] =
        IO.delay(
          KeychainInfo(
            KeychainId(UUID.randomUUID()),
            "",
            "",
            "",
            "",
            lookaheadSize,
            scheme,
            network
          )
        )

      override def getKeychainInfo(keychainId: KeychainId): IO[KeychainInfo] =
        IO.delay(
          KeychainInfo(
            KeychainId(keychainId.value),
            externalDescriptor = "externalDesc",
            internalDescriptor = "internalDesc",
            extendedPublicKey = "extendedPublicKey",
            slip32ExtendedPublicKey = "slip32ExtendedPublicKey",
            lookaheadSize = lookaheadSize,
            scheme = Scheme.Bip44,
            network = BitcoinNetwork.MainNet
          )
        )

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
              AccountAddress(_, ChangeType.External, derivation = NonEmptyList.one(1))
            )
          )

          newAddresses <- getAddresses(
            keychainId,
            newlyMarkedAddresses.size - 1,
            newlyMarkedAddresses.size + lookaheadSize - 1
          )

        } yield knownAddresses ++ newAddresses

      override def getFreshAddresses(
          keychainId: KeychainId,
          change: ChangeType,
          size: Int
      ): IO[List[AccountAddress]] = ???

      override def getAddressesPublicKeys(
          keychainId: KeychainId,
          derivations: List[List[Int]]
      ): IO[List[String]] = ???

      override def deleteKeychain(keychainId: KeychainId): IO[Unit] = ???

      override def resetKeychain(keychainId: KeychainId): IO[Unit] = ???

    }

}
