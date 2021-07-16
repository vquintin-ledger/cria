package co.ledger.cria.domain.adapters.persistence.wd.models

import java.math.BigInteger
import java.security.MessageDigest

import co.ledger.cria.domain.models.interpreter.{BlockHash, BlockView, Coin}

case class WDBlock(uid: String, hash: String, height: Long, time: String, currencyName: String)

object WDBlock {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromBlock(block: BlockView, coin: Coin): WDBlock =
    WDBlock(
      uid = computeUid(block.hash, coin),
      hash = block.hash.asString,
      height = block.height,
      time = block.time.toString,
      currencyName = coin.name
    )

  def computeUid(hash: BlockHash, coin: Coin): String = {
    String.format(
      "%064x",
      new BigInteger(
        1,
        digester.digest(s"uid:${hash.asString}+${coin.name}".getBytes("UTF-8"))
      )
    )
  }
}
