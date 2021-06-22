package co.ledger.cria.utils

import co.ledger.cria.models.account.Coin
import co.ledger.cria.models.keychain.{BitcoinLikeNetwork, BitcoinNetwork, LitecoinNetwork}

object CoinImplicits {

  implicit class CoinBitcoinUtils(coin: Coin) {

    def toNetwork: BitcoinLikeNetwork = {
      coin match {
        case Coin.Btc        => BitcoinNetwork.MainNet
        case Coin.BtcTestnet => BitcoinNetwork.TestNet3
        case Coin.BtcRegtest => BitcoinNetwork.RegTest
        case Coin.Ltc        => LitecoinNetwork.MainNet
        case _               => BitcoinNetwork.Unspecified
      }
    }

  }

}
