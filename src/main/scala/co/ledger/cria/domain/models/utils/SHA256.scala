package co.ledger.cria.domain.models.utils

import cats.Order
import doobie.Meta
import doobie.refined.implicits.refinedMeta
import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.And
import eu.timepit.refined.collection.Size
import eu.timepit.refined.generic.Equal
import eu.timepit.refined.string.HexStringSpec
import eu.timepit.refined.auto._
import io.circe.{Decoder, Encoder}
import io.circe.refined.{refinedDecoder, refinedEncoder}
import eu.timepit.refined.types.digests

case class SHA256 private (private val hash: digests.SHA256) {
  def asString: String = hash.value
}

object SHA256 {
  private type P = HexStringSpec And Size[Equal[64]]

  implicit val ordering: Order[SHA256] = Order.by(_.asString)

  private val hashEncoder: Encoder[digests.SHA256] = refinedEncoder
  private val hashDecoder: Decoder[digests.SHA256] = refinedDecoder

  implicit val encoder: Encoder[SHA256] = hashEncoder.contramap(_.hash)
  implicit val decoder: Decoder[SHA256] = hashDecoder.map(SHA256(_))

  // TODO Move to adaptation layer when dependency split for persistence is done
  implicit val metaHash: Meta[SHA256] =
    refinedMeta[String, P, Refined].timap(SHA256(_))(_.hash)

  def fromString(value: String): Either[String, SHA256] =
    digests.SHA256.from(value).map(SHA256(_))

  def fromStringUnsafe(value: String): SHA256 =
    fromString(value).getOrElse(
      throw new IllegalArgumentException(s"$value is not a valid SHA-256")
    )
}
