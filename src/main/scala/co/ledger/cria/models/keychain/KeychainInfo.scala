package co.ledger.cria.models.keychain

import co.ledger.cria.models.account.Scheme
import co.ledger.cria.models.circeImplicits._
import co.ledger.cria.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

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

  implicit val encoder: Encoder[KeychainInfo] = deriveConfiguredEncoder[KeychainInfo]
  implicit val decoder: Decoder[KeychainInfo] = deriveConfiguredDecoder[KeychainInfo]

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
