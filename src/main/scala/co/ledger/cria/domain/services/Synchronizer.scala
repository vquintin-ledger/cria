package co.ledger.cria.domain.services

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.cria.domain.models.account.Account
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.ChangeType
import co.ledger.cria.domain.models.keychain.ChangeType.{External, Internal}
import co.ledger.cria.domain.models.{SynchronizationParameters, SynchronizationResult}
import co.ledger.cria.domain.services.explorer.ExplorerClient
import co.ledger.cria.domain.services.interpreter.Interpreter
import co.ledger.cria.domain.services.keychain.{Keychain, KeychainClient}
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.utils.IOUtils
import fs2.Stream

import scala.math.Ordering.Implicits._

class Synchronizer(
    keychainClient: KeychainClient,
    explorerClient: Coin => ExplorerClient,
    interpreterClient: Interpreter,
    cursorService: Coin => CursorStateService[IO]
) extends ContextLogging {

  def run(
      syncParams: SynchronizationParameters
  )(implicit cs: ContextShift[IO], t: Timer[IO]): IO[SynchronizationResult] = {

    val account = Account(syncParams.accountUid, syncParams.keychainId, syncParams.coin)

    implicit val lc: CriaLogContext =
      CriaLogContext().withAccount(account).withCorrelationId(syncParams.syncId)

    IOUtils
      .withTimer("Synchronization finished")(
        for {
          _ <- log.info(s"Starting sync")
          cursorBlock <- syncParams.blockHash
            .map(reseedFromBlockHash(account))
            .getOrElse(getLastKnownBlock(account))

          _ <- log.info(
            s"Synchronizing from ${cursorBlock.map(b => s"block hash ${b.hash}").getOrElse("start")}"
          )

          res <- synchronizeAccount(syncParams, account, cursorBlock)
        } yield res
      )
      // In case of error, fallback to a reportable failed event.
      .handleError(error => SynchronizationResult.SynchronizationFailure(syncParams, error))
      .flatTap {
        case _: SynchronizationResult.SynchronizationSuccess =>
          log.info(s"Synchronization success !")
        case f: SynchronizationResult.SynchronizationFailure =>
          log.error(s"Synchronization failure : $f", f.throwable)
      }
  }

  private def getLastKnownBlock(account: Account)(implicit t: Timer[IO], lc: CriaLogContext) = {
    interpreterClient
      .getLastBlockHash(account.accountUid)
      .flatMap(hashO => hashO.map(explorerClient(account.coin).getBlock).getOrElse(IO.pure(None)))
  }

  private def reseedFromBlockHash(account: Account)(hash: BlockHash)(implicit
      t: Timer[IO],
      lc: CriaLogContext
  ) = {
    explorerClient(account.coin).getBlock(hash).flatTap { blockO =>
      blockO.traverse { block =>
        interpreterClient.removeDataFromCursor(account.accountUid, block.height)
      }
    }
  }

  def synchronizeAccount(
      syncParams: SynchronizationParameters,
      account: Account,
      previousBlockState: Option[BlockView]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): IO[SynchronizationResult] = {

    val keychain = new Keychain(keychainClient)

    val bookkeeper = Bookkeeper(
      keychain,
      explorerClient,
      interpreterClient
    )

    val keychainId = account.identifier

    // sync the whole account per streamed batch
    for {

      //TODO: Move memool sync after reorg
      addressesUsedByMempool <- (bookkeeper
        .record[Confirmation.Unconfirmed](
          account.coin,
          account.accountUid,
          keychainId,
          Internal,
          None
        ) ++ bookkeeper
        .record[Confirmation.Unconfirmed](
          account.coin,
          account.accountUid,
          keychainId,
          External,
          None
        )).compile.toList

      lastMinedBlock <- lastMinedBlock(account.coin)

      blockHeightAndAddressesUsed <- Stream
        .emit(previousBlockState)
        .filter {
          case Some(previousBlock) => previousBlock < lastMinedBlock.block
          case None                => true
        }
        .evalTap(b => log.info(s"Syncing blockchain transactions from cursor state: $b"))
        .evalMap(b =>
          b.map(previousBlock => rewindToLastValidBlock(account, previousBlock, syncParams.syncId))
            .sequence
        )
        .evalMap { lastValidBlock =>
          (bookkeeper
            .record[Confirmation.Confirmed](
              account.coin,
              account.accountUid,
              keychainId,
              ChangeType.Internal,
              lastValidBlock.map(_.hash)
            ) ++ bookkeeper
            .record[Confirmation.Confirmed](
              account.coin,
              account.accountUid,
              keychainId,
              ChangeType.External,
              lastValidBlock.map(_.hash)
            )).compile.toList.map((lastValidBlock, _))
        }
        .compile
        .lastOrError
      (blockView, addressesUsed) = blockHeightAndAddressesUsed
      _ <- log.info(s"Last block synchronized: ${lastMinedBlock.block}")

      _ <- IOUtils.withTimer("Computation finished")(
        interpreterClient.compute(account, syncParams.walletUid, blockView.map(_.height))(
          (addressesUsed ++ addressesUsedByMempool).distinct
        )
      )

    } yield {
      // Create the reportable successful event.
      SynchronizationResult.SynchronizationSuccess(syncParams, lastMinedBlock.block)
    }
  }

  case class LastMinedBlock(block: BlockView)

  def lastMinedBlock(coin: Coin)(implicit t: Timer[IO], lc: CriaLogContext): IO[LastMinedBlock] =
    explorerClient(coin).getCurrentBlock.map(LastMinedBlock)

  private def rewindToLastValidBlock(account: Account, lastKnownBlock: BlockView, syncId: SyncId)(
      implicit lc: CriaLogContext
  ): IO[BlockView] =
    for {

      lvb <- cursorService(account.coin).getLastValidState(account, lastKnownBlock, syncId)

      _ <- log.info(s"Last valid block : $lvb")
      _ <-
        if (lvb.hash == lastKnownBlock.hash)
          // If the previous block is still valid, do not reorg
          IO.unit
        else {
          // remove all transactions and operations up until last valid block
          log.info(
            s"${lastKnownBlock.hash} is different than ${lvb.hash}, reorg is happening"
          ) *> interpreterClient.removeDataFromCursor(account.accountUid, lvb.height)
        }
    } yield lvb

}
