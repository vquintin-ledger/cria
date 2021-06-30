package co.ledger.cria.itutils.models.keychain

import co.ledger.cria.clients.explorer.types.Coin

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
