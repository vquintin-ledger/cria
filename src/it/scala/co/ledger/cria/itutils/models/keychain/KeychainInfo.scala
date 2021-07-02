package co.ledger.cria.itutils.models.keychain

import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.cria.domain.models.circeImplicits._

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
