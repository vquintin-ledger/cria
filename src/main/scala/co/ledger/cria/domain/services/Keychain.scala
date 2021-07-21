package co.ledger.cria.domain.services

import cats.effect.IO
import Keychain.{Address, addressesRanges}
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import fs2.Stream
import co.ledger.cria.logging.{ContextLogging, CriaLogContext, DefaultContextLogging}

class Keychain(client: KeychainClient) extends ContextLogging {

  def markAsUsed(id: KeychainId, addresses: Set[Address]): IO[Unit] =
    client
      .markAddressesAsUsed(id, addresses.toList)

  def discoverAddresses(
      id: KeychainId,
      change: Option[ChangeType] = None,
      from: Int = 0
  )(implicit lc: CriaLogContext): Stream[IO, List[AccountAddress]] = {
    Stream
      .eval(client.getLookaheadSize(id))
      .flatMap(lookaheadSize => addressesRanges(size = lookaheadSize, start = from))
      .evalMap(r =>
        for {
          newAddresses <- client.getAddresses(id, r.head, r.last + 1, change)
          _ <- log.info(
            s"Discovering ${newAddresses.size} new ${change.map(_.name + " ").getOrElse("")}addresses from index : ${r.head}"
          )
        } yield newAddresses
      )
  }

  def knownAndNewAddresses(
      id: KeychainId,
      change: Option[ChangeType] = None
  )(implicit lc: CriaLogContext): Stream[IO, List[AccountAddress]] =
    Stream.eval(for {
      knownAddresses <- client.getKnownAndNewAddresses(id, change)
      _ <- log.info(
        s"Number of known ${change.map(_.name + " ").getOrElse("")}addresses found : ${knownAddresses.size - 20}"
      )
    } yield knownAddresses)

}

object Keychain extends ContextLogging with DefaultContextLogging {
  type Address = String

  def addressesRanges[F[_]](size: Int, start: Int = 0): Stream[F, Range] = {
    val from = Stream.iterate(start)(_ + size)
    val to   = Stream.iterate(start + size)(_ + size)

    from
      .zip(to)
      .map(r => r._1 until r._2)
  }
}
