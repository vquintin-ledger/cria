package co.ledger.cria.models.interpreter

import cats.data.NonEmptyList
import co.ledger.cria.models.circeImplicits._
import co.ledger.protobuf.bitcoin.keychain
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class AccountAddress(
    accountAddress: String,
    changeType: ChangeType,
    derivation: NonEmptyList[Int]
)

object AccountAddress {
  implicit val encoder: Encoder[AccountAddress] = deriveConfiguredEncoder[AccountAddress]
  implicit val decoder: Decoder[AccountAddress] = deriveConfiguredDecoder[AccountAddress]

  def fromKeychainProto(proto: keychain.AddressInfo): AccountAddress =
    AccountAddress(
      proto.address,
      ChangeType.fromKeychainProto(proto.change),
      NonEmptyList.fromListUnsafe(proto.derivation.toList)
    )
}
