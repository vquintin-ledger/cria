package co.ledger.cria.domain.adapters.keychain

import cats.effect.{ContextShift, IO}
import co.ledger.cria.clients.protocol.grpc.GrpcClient
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import co.ledger.cria.domain.services.keychain.KeychainClient
import co.ledger.cria.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain
import io.grpc.{ManagedChannel, Metadata}

final class KeychainGrpcClient(
    val managedChannel: ManagedChannel
)(implicit val cs: ContextShift[IO])
    extends KeychainClient {

  val client: keychain.KeychainServiceFs2Grpc[IO, Metadata] =
    GrpcClient.resolveClient(
      keychain.KeychainServiceFs2Grpc.stub[IO],
      managedChannel,
      "keychainClient"
    )

  override def getLookaheadSize(keychainId: KeychainId): IO[Int] =
    client
      .getKeychainInfo(
        keychain.GetKeychainInfoRequest(UuidUtils.uuidToBytes(keychainId.value)),
        new Metadata
      )
      .map(_.lookaheadSize)

  override def getAddresses(
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
              change = PBHelper.changeType.toKeychainProto(change)
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
      .map(_.addresses.map(PBHelper.accountAddress.fromKeychainProto).toList)

  override def markAddressesAsUsed(keychainId: KeychainId, addresses: List[String]): IO[Unit] =
    client
      .markAddressesAsUsed(
        keychain.MarkAddressesAsUsedRequest(UuidUtils.uuidToBytes(keychainId.value), addresses),
        new Metadata
      )
      .void

  // getAllObservableAddresses with no indexes given will always return all known addresses
  // plus 20 new addresses (for free !)
  override def getKnownAndNewAddresses(
      keychainId: KeychainId,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]] =
    client
      .getAllObservableAddresses(
        changeType
          .map(change =>
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId.value),
              change = PBHelper.changeType.toKeychainProto(change)
            )
          )
          .getOrElse(
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId.value)
            )
          ),
        new Metadata
      )
      .map(_.addresses.map(PBHelper.accountAddress.fromKeychainProto).toList)
}
