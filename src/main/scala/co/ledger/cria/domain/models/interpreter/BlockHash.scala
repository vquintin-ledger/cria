package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.utils.SHA256
import io.circe.{Decoder, Encoder}

case class BlockHash(hash: SHA256) extends AnyVal {
  def asString: String = hash.asString
}

object BlockHash {

  def fromString(value: String): Either[String, BlockHash] =
    SHA256.fromString(value).map(BlockHash(_))

  def fromStringUnsafe(value: String): BlockHash =
    BlockHash(SHA256.fromStringUnsafe(value))

  implicit val encoder: Encoder[BlockHash] = Encoder[SHA256].contramap(_.hash)
  implicit val decoder: Decoder[BlockHash] = Decoder[SHA256].map(BlockHash(_))
}
