package co.ledger.cria.e2e.recovery

import cats.effect.IO
import cats.implicits.catsSyntaxFlatMapOps
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import co.ledger.cria.domain.services.KeychainClient

final class KeychainClientRunBefore(keychainClient: KeychainClient, action: IO[Unit])
    extends KeychainClient {
  override def getLookaheadSize(keychainId: KeychainId): IO[Int] =
    action >> keychainClient.getLookaheadSize(keychainId)

  override def getAddresses(
      keychainId: KeychainId,
      fromIndex: Int,
      toIndex: Int,
      changeType: Option[ChangeType]
  ): IO[List[AccountAddress]] =
    action >> keychainClient.getAddresses(keychainId, fromIndex, toIndex, changeType)

  override def markAddressesAsUsed(keychainId: KeychainId, addresses: List[String]): IO[Unit] =
    action >> keychainClient.markAddressesAsUsed(keychainId, addresses)

  override def getKnownAndNewAddresses(
      keychainId: KeychainId,
      changeType: Option[ChangeType]
  ): IO[List[AccountAddress]] =
    action >> keychainClient.getKnownAndNewAddresses(keychainId, changeType)
}
