package co.ledger.cria.domain.models

import cats.Order
import co.ledger.cria.domain.models.utils.SHA256
import doobie.Meta
import io.circe.{Decoder, Encoder}

case class TxHash(private val hash: SHA256) extends AnyVal {
  def asString: String = hash.asString
}

object TxHash {
  implicit val ordering: Order[TxHash] = Order.by(_.hash)

  // TODO why do we need circe anyway ?
  implicit val encoder: Encoder[TxHash] = Encoder[SHA256].contramap(_.hash)
  implicit val decoder: Decoder[TxHash] = Decoder[SHA256].map(TxHash(_))

  // TODO Move to adaptation layer when dependency split for persistence is done
  implicit val metaHash: Meta[TxHash] = Meta[SHA256].timap(TxHash(_))(_.hash)

  def fromString(value: String): Either[String, TxHash] =
    SHA256.fromString(value).map(TxHash(_))

  def fromStringUnsafe(value: String): TxHash =
    TxHash(SHA256.fromStringUnsafe(value))
}
