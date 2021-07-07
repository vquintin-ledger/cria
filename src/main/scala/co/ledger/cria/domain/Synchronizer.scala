package co.ledger.cria.domain

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.cria.domain.models.account.Account
import co.ledger.cria.domain.models.interpreter._
import co.ledger.cria.domain.models.keychain.ChangeType
import co.ledger.cria.domain.models.keychain.ChangeType.{External, Internal}
import co.ledger.cria.domain.models.{SynchronizationParameters, SynchronizationResult}
import co.ledger.cria.domain.services.interpreter.Interpreter
import co.ledger.cria.domain.services._
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
  )(implicit cs: ContextShift[IO], t: Timer[IO]): IO[SynchronizationResult] =
    getOrCreateAccount(syncParams).flatMap { account =>
      implicit val lc: CriaLogContext =
        CriaLogContext().withAccount(account).withCorrelationId(syncParams.syncId)

      val result = IOUtils.withTimer("Synchronization finished")(
        for {
          _           <- log.info(s"Starting sync")
          cursorBlock <- syncParams.blockHash.flatTraverse(explorerClient(syncParams.coin).getBlock)
          res         <- synchronizeAccount(syncParams, account, cursorBlock)
        } yield res
      )

      // In case of error, fallback to a reportable failed event.
      result
        .handleError(error => SynchronizationResult.SynchronizationFailure(syncParams, error))
        .flatTap {
          case _: SynchronizationResult.SynchronizationSuccess =>
            log.info(s"Synchronization success !")
          case f: SynchronizationResult.SynchronizationFailure =>
            log.error(s"Synchronization failure : $f", f.throwable)
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

      addressesUsedByMempool <- (bookkeeper
        .record[Confirmation.Unconfirmed](
          account.coin,
          account.id,
          keychainId,
          Internal,
          None
        ) ++ bookkeeper
        .record[Confirmation.Unconfirmed](
          account.coin,
          account.id,
          keychainId,
          External,
          None
        )).compile.toList

      lastMinedBlock <- lastMinedBlock(account.coin)

      addressesUsed <- Stream
        .emit(previousBlockState)
        .filter {
          case Some(previous) => previous < lastMinedBlock.block
          case None           => true
        }
        .evalTap(b => log.info(s"Syncing blockchain transactions from cursor state: $b"))
        .evalMap(b => b.map(rewindToLastValidBlock(account, _, syncParams.syncId)).sequence)
        .evalMap { lastValidBlock =>
          (bookkeeper
            .record[Confirmation.Confirmed](
              account.coin,
              account.id,
              keychainId,
              ChangeType.Internal,
              lastValidBlock.map(_.hash)
            ) ++ bookkeeper
            .record[Confirmation.Confirmed](
              account.coin,
              account.id,
              keychainId,
              ChangeType.External,
              lastValidBlock.map(_.hash)
            )).compile.toList
        }
        .compile
        .toList
        .map(_.flatten)

      _ <- log.info(s"Last block synchronized: ${lastMinedBlock.block}")

      _ <- IOUtils.withTimer("Computation finished")(
        interpreterClient.compute(
          account,
          syncParams.syncId,
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
          ) *> interpreterClient.removeDataFromCursor(account.id, Some(lvb.height), syncId)
        }
    } yield lvb

  private def getOrCreateAccount(syncParams: SynchronizationParameters): IO[Account] =
    for {

      _ <- log.info(s"""Registering account on keychain with params : 
          - keychainId: ${syncParams.keychainId}
          - coin      : ${syncParams.coin}""")(
        CriaLogContext().withCorrelationId(syncParams.syncId)
      )
      account = Account(syncParams.keychainId, syncParams.coin)

    } yield account
}
