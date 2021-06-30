package co.ledger.cria.clients.grpc

import cats.effect.{ContextShift, IO}
import co.ledger.cria.models.interpreter.{AccountAddress, ChangeType}
import co.ledger.cria.models.keychain.{AccountKey, BitcoinLikeNetwork, KeychainId, KeychainInfo}
import co.ledger.cria.models.account.Scheme
import co.ledger.cria.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain
import io.grpc._

trait KeychainClient {
  def create(
      accountKey: AccountKey,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinLikeNetwork
  ): IO[KeychainInfo]

  def getKeychainInfo(keychainId: KeychainId): IO[KeychainInfo]

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

  def getFreshAddresses(
      keychainId: KeychainId,
      change: ChangeType,
      size: Int
  ): IO[List[AccountAddress]]

  def getAddressesPublicKeys(
      keychainId: KeychainId,
      derivations: List[List[Int]]
  ): IO[List[String]]

  def resetKeychain(keychainId: KeychainId): IO[Unit]

  def deleteKeychain(keychainId: KeychainId): IO[Unit]
}

class KeychainGrpcClient(
    val managedChannel: ManagedChannel
)(implicit val cs: ContextShift[IO])
    extends KeychainClient {

  val client: keychain.KeychainServiceFs2Grpc[IO, Metadata] =
    GrpcClient.resolveClient(
      keychain.KeychainServiceFs2Grpc.stub[IO],
      managedChannel,
      "keychainClient"
    )

  def create(
      accountKey: AccountKey,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinLikeNetwork
  ): IO[KeychainInfo] =
    client
      .createKeychain(
        keychain.CreateKeychainRequest(
          accountKey.toProto,
          scheme.toProto,
          lookaheadSize,
          Some(network.toKeychainChainParamsProto)
        ),
        new Metadata
      )
      .map(KeychainInfo.fromProto)

  def getKeychainInfo(keychainId: KeychainId): IO[KeychainInfo] =
    client
      .getKeychainInfo(
        keychain.GetKeychainInfoRequest(UuidUtils.uuidToBytes(keychainId.value)),
        new Metadata
      )
      .map(KeychainInfo.fromProto)

  def getAddresses(
      keychainId: KeychainId,
      fromIndex: Int,
      toIndex: Int,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]] =
    client
      .getAllObservableAddresses(
        changeType // not the best way to handle...
          .map(change =>
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId.value),
              fromIndex = fromIndex,
              toIndex = toIndex,
              change = change.toKeychainProto
            )
          )
          .getOrElse(
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId.value),
              fromIndex = fromIndex,
              toIndex = toIndex
            )
          ),
        new Metadata
      )
      .map(_.addresses.map(AccountAddress.fromKeychainProto).toList)

  def markAddressesAsUsed(keychainId: KeychainId, addresses: List[String]): IO[Unit] =
    client
      .markAddressesAsUsed(
        keychain.MarkAddressesAsUsedRequest(UuidUtils.uuidToBytes(keychainId.value), addresses),
        new Metadata
      )
      .void

  // getAllObservableAddresses with no indexes given will always return all known addresses
  // plus 20 new addresses (for free !)

  def getKnownAndNewAddresses(
      keychainId: KeychainId,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]] =
    client
      .getAllObservableAddresses(
        changeType
          .map(change =>
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId.value),
              change = change.toKeychainProto
            )
          )
          .getOrElse(
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId.value)
            )
          ),
        new Metadata
      )
      .map(_.addresses.map(AccountAddress.fromKeychainProto).toList)

  def getFreshAddresses(
      keychainId: KeychainId,
      change: ChangeType,
      size: Int
  ): IO[List[AccountAddress]] =
    client
      .getFreshAddresses(
        keychain.GetFreshAddressesRequest(
          UuidUtils.uuidToBytes(keychainId.value),
          change.toKeychainProto,
          size
        ),
        new Metadata
      )
      .map(_.addresses.map(AccountAddress.fromKeychainProto).toList)

  def getAddressesPublicKeys(
      keychainId: KeychainId,
      derivations: List[List[Int]]
  ): IO[List[String]] =
    client
      .getAddressesPublicKeys(
        keychain.GetAddressesPublicKeysRequest(
          UuidUtils.uuidToBytes(keychainId.value),
          derivations
            .map(derivation => keychain.DerivationPath(derivation))
        ),
        new Metadata
      )
      .map(_.publicKeys.toList)

  def resetKeychain(keychainId: KeychainId): IO[Unit] =
    client
      .resetKeychain(
        keychain.ResetKeychainRequest(
          keychainId = UuidUtils.uuidToBytes(keychainId.value)
        ),
        new Metadata
      )
      .void

  def deleteKeychain(keychainId: KeychainId): IO[Unit] =
    client
      .deleteKeychain(
        keychain.DeleteKeychainRequest(
          keychainId = UuidUtils.uuidToBytes(keychainId.value)
        ),
        new Metadata
      )
      .void
}
