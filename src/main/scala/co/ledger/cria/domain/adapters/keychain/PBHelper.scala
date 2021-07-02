package co.ledger.cria.domain.adapters.keychain

import cats.data.NonEmptyList
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType}
import co.ledger.cria.domain.models.keychain.ChangeType.{External, Internal}
import co.ledger.protobuf.bitcoin.keychain

object PBHelper {
  object accountAddress {

    def fromKeychainProto(proto: keychain.AddressInfo): AccountAddress =
      AccountAddress(
        proto.address,
        changeType.fromKeychainProto(proto.change),
        NonEmptyList.fromListUnsafe(proto.derivation.toList)
      )
  }

  object changeType {
    def fromKeychainProto(proto: keychain.Change): ChangeType = {
      proto match {
        case keychain.Change.CHANGE_INTERNAL => Internal
        case _                               => External
      }
    }

    def toKeychainProto(ct: ChangeType): keychain.Change =
      ct match {
        case ChangeType.Internal => keychain.Change.CHANGE_INTERNAL
        case ChangeType.External => keychain.Change.CHANGE_EXTERNAL
      }
  }
}
