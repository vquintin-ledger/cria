package co.ledger.cria.itutils.models.keychain

import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain

case class KeychainInfo(
    keychainId: KeychainId,
    externalDescriptor: String,
    internalDescriptor: String,
    extendedPublicKey: String,
    slip32ExtendedPublicKey: String,
    lookaheadSize: Int,
    scheme: Scheme,
    network: BitcoinLikeNetwork
) {
  def toProto: keychain.KeychainInfo =
    keychain.KeychainInfo(
      UuidUtils.uuidToBytes(keychainId.value),
      externalDescriptor,
      internalDescriptor,
      extendedPublicKey,
      slip32ExtendedPublicKey,
      lookaheadSize,
      scheme.toProto,
      Some(network.toKeychainChainParamsProto)
    )
}

object KeychainInfo {
  def fromProto(proto: keychain.KeychainInfo): KeychainInfo =
    KeychainInfo(
      KeychainId(UuidUtils.unsafeBytesToUuid(proto.keychainId)),
      proto.externalDescriptor,
      proto.internalDescriptor,
      proto.extendedPublicKey,
      proto.slip32ExtendedPublicKey,
      proto.lookaheadSize,
      Scheme.fromProto(proto.scheme),
      proto.chainParams.map(BitcoinLikeNetwork.fromProto).getOrElse(BitcoinNetwork.Unspecified)
    )
}
