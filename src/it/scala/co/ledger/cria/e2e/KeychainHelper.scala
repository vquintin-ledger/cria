package co.ledger.cria.e2e

import cats.effect.IO
import co.ledger.cria.domain.models.keychain.KeychainId
import co.ledger.cria.itutils.ContainerFlatSpec
import co.ledger.cria.itutils.models.keychain.CoinImplicits.CoinBitcoinUtils
import co.ledger.cria.itutils.models.keychain.KeychainInfo
import co.ledger.protobuf.bitcoin.keychain
import io.grpc.Metadata

trait KeychainHelper { cfs: ContainerFlatSpec =>

  def makeKeychainId(request: RegisterRequest): IO[KeychainId] =
    testResources.use { tr =>
      tr.rawKeychainClient
        .createKeychain(
          keychain.CreateKeychainRequest(
            request.accountKey.toProto,
            request.scheme.toProto,
            request.lookaheadSize,
            Some(request.coin.toNetwork.toKeychainChainParamsProto)
          ),
          new Metadata
        )
        .map(KeychainInfo.fromProto)
        .map(_.keychainId)
    }
}
