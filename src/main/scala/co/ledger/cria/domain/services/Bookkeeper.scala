package co.ledger.cria.domain.services

import cats.effect.{ContextShift, IO, Timer}
import fs2.{Pipe, Stream}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockHash, Coin, Confirmation, TransactionView}
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import co.ledger.cria.domain.services.explorer.ExplorerClient
import co.ledger.cria.domain.services.interpreter.Interpreter
import co.ledger.cria.domain.services.keychain.Keychain
import shapeless.tag.@@

trait Bookkeeper[F[_]] {
  def record[Tx <: Confirmation: Bookkeeper.Recordable](
      coin: Coin,
      accountId: AccountUid,
      keychainId: KeychainId,
      change: ChangeType,
      blockHash: Option[BlockHash]
  )(implicit lc: CriaLogContext): Stream[F, AccountAddress]
}

object Bookkeeper extends ContextLogging {
  type Address = String

  def apply(
      keychain: Keychain,
      explorerClient: Coin => ExplorerClient,
      interpreterClient: Interpreter
  )(implicit cs: ContextShift[IO]): Bookkeeper[IO] = new Bookkeeper[IO] {

    override def record[Tx <: Confirmation: Recordable](
        coin: Coin,
        accountId: AccountUid,
        keychainId: KeychainId,
        change: ChangeType,
        blockHash: Option[BlockHash]
    )(implicit lc: CriaLogContext): Stream[IO, AccountAddress] = {
      val keychainAddresses = for {

        // knownAndNewAddresses will provided addresses previously marked as used AND $lookahead[20] new addresses.
        knownAddresses <- keychain.knownAndNewAddresses(keychainId, Some(change))

        addresses <- Stream
          .emit(knownAddresses) ++ keychain.discoverAddresses(
          keychainId,
          Some(change),
          knownAddresses.size - 1
        )

      } yield addresses

      keychainAddresses
        .flatMap { addresses =>
          Stream
            .emit(addresses)
            .evalTap(addresses =>
              log.info(s"Fetching explorer for ${change.name} ${addresses.size} addresses")
            )
            .through(Bookkeeper.fetchTransactionRecords(explorerClient(coin), blockHash))
            .through(Bookkeeper.saveTransactionRecords(interpreterClient, accountId))
            .foldMonoid
            .through(Bookkeeper.markAddresses(keychain, keychainId))
        }
        .takeWhile(_.nonEmpty)
        .foldMonoid
        .evalTap(_ =>
          log.info(s"No tx found for ${change.name} addresses on last batch - ending search")
        )
        .flatMap(Stream.emits(_))
    }

  }

  case class TransactionRecord[Tx <: Confirmation: Recordable](
      tx: TransactionView @@ Tx,
      usedAddresses: List[AccountAddress]
  )

  def fetchTransactionRecords[Tx <: Confirmation](
      explorer: ExplorerClient,
      blockHash: Option[BlockHash]
  )(implicit
      cs: ContextShift[IO],
      recordable: Recordable[Tx]
  ): Pipe[IO, List[AccountAddress], TransactionRecord[Tx]] =
    _.prefetch
      .flatMap { addresses =>
        recordable
          .fetch(explorer)(addresses.map(_.accountAddress).toSet, blockHash)
          .map(tx => TransactionRecord(tx, addressesUsed(addresses)(tx)))
      }

  def saveTransactionRecords[Tx <: Confirmation: Recordable](
      interpreter: Interpreter,
      accountId: AccountUid
  )(implicit
      recordable: Recordable[Tx],
      lc: CriaLogContext
  ): Pipe[IO, TransactionRecord[Tx], List[AccountAddress]] =
    _.chunkN(100).flatMap { chunk =>
      Stream
        .chunk(chunk.map(_.tx))
        .through(recordable.save(interpreter)(accountId))
        .as(chunk.map(a => a.usedAddresses).toList.flatten)
    }

  def addressUsedBy(tx: TransactionView)(accountAddress: AccountAddress): Boolean = {
    tx.inputs
      .map(_.address)
      .contains(accountAddress.accountAddress) ||
    tx.outputs.map(_.address).contains(accountAddress.accountAddress)
  }

  def addressesUsed(
      accountAddresses: List[AccountAddress]
  )(tx: TransactionView): List[AccountAddress] =
    accountAddresses.filter(addressUsedBy(tx)).distinct

  def markAddresses[Tx <: Confirmation](
      keychain: Keychain,
      keychainId: KeychainId
  )(implicit lc: CriaLogContext): Pipe[IO, List[AccountAddress], List[AccountAddress]] =
    _.evalTap { addresses =>
      val usedAddresses = addresses.distinct.map(_.accountAddress).toSet
      log.info(s"Marking ${usedAddresses.size} addresses as used") *>
        log.debug(s"addresses : ${usedAddresses.mkString(", ")}") *>
        keychain.markAsUsed(keychainId, usedAddresses)
    }

  trait Recordable[T <: Confirmation] {
    def fetch(
        explorer: ExplorerClient
    )(addresses: Set[Address], block: Option[BlockHash]): Stream[IO, TransactionView @@ T]

    def save(interpreter: Interpreter)(accountId: AccountUid)(implicit
        lc: CriaLogContext
    ): Pipe[IO, TransactionView, Unit] =
      interpreter.saveTransactions(accountId)
  }

  implicit def confirmed(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Recordable[Confirmation.Confirmed] =
    new Recordable[Confirmation.Confirmed] {
      override def fetch(
          explorer: ExplorerClient
      )(
          addresses: Set[Address],
          block: Option[BlockHash]
      ): Stream[IO, TransactionView @@ Confirmation.Confirmed] =
        explorer
          .getConfirmedTransactions(addresses.toSeq, block)
    }

  implicit def unconfirmedTransaction(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Recordable[Confirmation.Unconfirmed] =
    new Recordable[Confirmation.Unconfirmed] {
      override def fetch(
          explorer: ExplorerClient
      )(
          addresses: Set[Address],
          block: Option[BlockHash]
      ): Stream[IO, TransactionView @@ Confirmation.Unconfirmed] =
        explorer
          .getUnconfirmedTransactions(addresses)
    }
}
