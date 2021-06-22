package co.ledger.cria

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.cria.clients.grpc.KeychainClient
import co.ledger.cria.clients.http.ExplorerClient
import co.ledger.cria.logging.{ContextLogging, LamaLogContext}
import co.ledger.cria.models.account.{Account, Coin, CoinFamily}
import co.ledger.cria.models.explorer.{Block, ConfirmedTransaction, UnconfirmedTransaction}
import co.ledger.cria.models.interpreter.ChangeType
import co.ledger.cria.models.interpreter.ChangeType.{External, Internal}
import co.ledger.cria.services._
import co.ledger.cria.services.interpreter.Interpreter
import co.ledger.cria.utils.CoinImplicits._
import fs2.Stream
import java.util.UUID

import scala.math.Ordering.Implicits._
import scala.util.Try

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
      implicit val lc: LamaLogContext =
        LamaLogContext().withAccount(account).withFollowUpId(syncParams.syncId)

      val result = for {
        cursorBlock <- syncParams.blockHash.flatTraverse(explorerClient(syncParams.coin).getBlock)
        res         <- synchronizeAccount(syncParams, account, cursorBlock)
      } yield res

      // In case of error, fallback to a reportable failed event.
      log.info(s"Received event: ${syncParams.toString}") *>
        result
          .handleError(error => SynchronizationResult.SynchronizationFailure(syncParams, error))
          .flatTap {
            case s: SynchronizationResult.SynchronizationSuccess =>
              log.info(s"Synchronization success: $s")
            case f: SynchronizationResult.SynchronizationFailure =>
              log.error(s"Synchronization failure: $f", f.throwable)
          }
    }

  def synchronizeAccount(
      syncParams: SynchronizationParameters,
      account: Account,
      previousBlockState: Option[Block]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: LamaLogContext
  ): IO[SynchronizationResult] = {

    val keychain = new Keychain(keychainClient)

    val bookkeeper = Bookkeeper(
      keychain,
      explorerClient,
      interpreterClient
    )

    // sync the whole account per streamed batch
    for {

      keychainId <- IO.fromTry(Try(UUID.fromString(account.identifier)))

      addressesUsedByMempool <- (bookkeeper
        .record[UnconfirmedTransaction](
          account.coin,
          account.id,
          keychainId,
          Internal,
          None
        ) ++ bookkeeper
        .record[UnconfirmedTransaction](
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
        .evalTap(b => log.info(s"Syncing from cursor state: $b"))
        .evalMap(b => b.map(rewindToLastValidBlock(account, _, syncParams.syncId)).sequence)
        .evalMap { lastValidBlock =>
          (bookkeeper
            .record[ConfirmedTransaction](
              account.coin,
              account.id,
              keychainId,
              ChangeType.Internal,
              lastValidBlock.map(_.hash)
            ) ++ bookkeeper
            .record[ConfirmedTransaction](
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

      _ <- log.info(s"New cursor state: ${lastMinedBlock.block}")

      opsCount <- interpreterClient.compute(
        account,
        syncParams.syncId,
        (addressesUsed ++ addressesUsedByMempool).distinct
      )

      _ <- log.info(s"$opsCount operations computed")

    } yield {
      // Create the reportable successful event.
      SynchronizationResult.SynchronizationSuccess(syncParams, lastMinedBlock.block)
    }
  }

  case class LastMinedBlock(block: Block)

  def lastMinedBlock(coin: Coin)(implicit lc: LamaLogContext): IO[LastMinedBlock] =
    explorerClient(coin).getCurrentBlock.map(LastMinedBlock)

  private def rewindToLastValidBlock(account: Account, lastKnownBlock: Block, syncId: UUID)(implicit
      lc: LamaLogContext
  ): IO[Block] =
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
    keychainClient
      .create(
        syncParams.xpub,
        syncParams.scheme,
        syncParams.lookahead,
        syncParams.coin.toNetwork
      )
      .map { info =>
        Account(info.keychainId.toString, CoinFamily.Bitcoin, syncParams.coin)
      }
}
