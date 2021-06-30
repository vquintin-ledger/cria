package co.ledger.cria.domain.services

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.clients.explorer.ExplorerClient
import co.ledger.cria.clients.explorer.types.{
  ConfirmedTransaction,
  DefaultInput,
  Transaction,
  UnconfirmedTransaction
}
import fs2.{Pipe, Stream}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.domain.models.account.{AccountId, Coin}
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import co.ledger.cria.domain.services.interpreter.Interpreter

trait Bookkeeper[F[_]] {
  def record[Tx <: Transaction: Bookkeeper.Recordable](
      coin: Coin,
      accountId: AccountId,
      keychainId: KeychainId,
      change: ChangeType,
      blockHash: Option[Bookkeeper.BlockHash]
  )(implicit lc: CriaLogContext): Stream[F, AccountAddress]
}

object Bookkeeper extends ContextLogging {
  type Address   = String
  type BlockHash = String

  def apply(
      keychain: Keychain,
      explorerClient: Coin => ExplorerClient,
      interpreterClient: Interpreter
  )(implicit cs: ContextShift[IO]): Bookkeeper[IO] = new Bookkeeper[IO] {

    override def record[Tx <: Transaction: Recordable](
        coin: Coin,
        accountId: AccountId,
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

  case class TransactionRecord[Tx <: Transaction: Recordable](
      tx: Tx,
      usedAddresses: List[AccountAddress]
  )

  def fetchTransactionRecords[Tx <: Transaction](
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

  def saveTransactionRecords[Tx <: Transaction: Recordable](
      interpreter: Interpreter,
      accountId: AccountId
  )(implicit
      recordable: Recordable[Tx],
      lc: CriaLogContext
  ): Pipe[IO, TransactionRecord[Tx], List[AccountAddress]] =
    _.chunks.flatMap { chunk =>
      Stream
        .chunk(chunk.map(_.tx))
        .through(recordable.save(interpreter)(accountId))
        .as(chunk.map(a => a.usedAddresses).toList.flatten)
    }

  def addressUsedBy(tx: Transaction)(accountAddress: AccountAddress): Boolean = {
    tx.inputs
      .collect { case i: DefaultInput => i.address }
      .contains(accountAddress.accountAddress) ||
    tx.outputs.map(_.address).contains(accountAddress.accountAddress)
  }

  def addressesUsed(
      accountAddresses: List[AccountAddress]
  )(tx: Transaction): List[AccountAddress] =
    accountAddresses.filter(addressUsedBy(tx)).distinct

  def markAddresses[Tx <: Transaction](
      keychain: Keychain,
      keychainId: KeychainId
  )(implicit lc: CriaLogContext): Pipe[IO, List[AccountAddress], List[AccountAddress]] =
    _.evalTap { addresses =>
      val usedAddresses = addresses.distinct.map(_.accountAddress).toSet
      log.info(s"Marking ${usedAddresses.size} addresses as used") *>
        log.debug(s"addresses : ${usedAddresses.mkString(", ")}") *>
        keychain.markAsUsed(keychainId, usedAddresses)
    }

  trait Recordable[Tx <: Transaction] {
    def fetch(
        explorer: ExplorerClient
    )(addresses: Set[Address], block: Option[BlockHash]): Stream[IO, Tx]

    def save(interpreter: Interpreter)(accountId: AccountId)(implicit
        lc: CriaLogContext
    ): Pipe[IO, Tx, Unit] =
      _.map(_.toTransactionView).through(interpreter.saveTransactions(accountId))
  }

  implicit def confirmed(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Recordable[ConfirmedTransaction] =
    new Recordable[ConfirmedTransaction] {
      override def fetch(
          explorer: ExplorerClient
      )(addresses: Set[Address], block: Option[BlockHash]): Stream[IO, ConfirmedTransaction] =
        explorer.getConfirmedTransactions(addresses.toSeq, block)
    }

  implicit def unconfirmedTransaction(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Recordable[UnconfirmedTransaction] =
    new Recordable[UnconfirmedTransaction] {
      override def fetch(
          explorer: ExplorerClient
      )(addresses: Set[Address], block: Option[BlockHash]): Stream[IO, UnconfirmedTransaction] =
        explorer.getUnconfirmedTransactions(addresses)
    }
}
