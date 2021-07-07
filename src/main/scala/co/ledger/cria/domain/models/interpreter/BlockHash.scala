package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.utils.SHA256

case class BlockHash(hash: SHA256) extends AnyVal {
  def asString: String = hash.asString
}

object BlockHash {

  def fromString(value: String): Either[String, BlockHash] =
    SHA256.fromString(value).map(BlockHash(_))

  def fromStringUnsafe(value: String): BlockHash =
    BlockHash(SHA256.fromStringUnsafe(value))
}
