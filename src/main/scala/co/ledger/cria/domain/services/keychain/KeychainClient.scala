package co.ledger.cria.domain.services.keychain

import cats.effect.IO
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}

trait KeychainClient {

  def getLookaheadSize(keychainId: KeychainId): IO[Int]

  def getAddresses(
      keychainId: KeychainId,
      fromIndex: Int,
      toIndex: Int,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]]

  def markAddressesAsUsed(keychainId: KeychainId, addresses: List[String]): IO[Unit]

  def getKnownAndNewAddresses(
      keychainId: KeychainId,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]]
}
