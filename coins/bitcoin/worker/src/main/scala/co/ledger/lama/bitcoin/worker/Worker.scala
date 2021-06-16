package co.ledger.lama.bitcoin.worker

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.lama.bitcoin.common.clients.grpc.{InterpreterClient, KeychainClient}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient
import co.ledger.lama.bitcoin.common.models.explorer.{
  Block,
  ConfirmedTransaction,
  UnconfirmedTransaction
}
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.logging.{ContextLogging, LamaLogContext}
import co.ledger.lama.common.models.{Account, Coin}
import fs2.Stream
import io.circe.syntax._

import java.util.UUID
import co.ledger.lama.bitcoin.common.models.interpreter.ChangeType

import scala.math.Ordering.Implicits._
import scala.util.Try

class Worker(
    args: SynchronizationParameters,
    keychainClient: KeychainClient,
    explorerClient: Coin => ExplorerClient,
    interpreterClient: InterpreterClient,
    cursorService: Coin => CursorStateService[IO]
) extends ContextLogging {

  def run(implicit cs: ContextShift[IO], t: Timer[IO]): IO[Unit] =
      getOrCreateAccount(args.xpub).flatMap { account =>
        implicit val lc: LamaLogContext =
          LamaLogContext().withAccount(account).withFollowUpId(args.syncId)

        val result = for {
          cursorBlock <- args.cursor.flatTraverse(explorerClient(Coin.Btc).getBlock)
          res <- synchronizeAccount(account, cursorBlock)
        } yield res

        // In case of error, fallback to a reportable failed event.
        log.info(s"Received event: ${args.asJson.toString}") *>
          result
            .handleError(error => SynchronizationResult.SynchronizationFailure(args, error))
            .map {
            case s: SynchronizationResult.SynchronizationSuccess =>
              log.info(s"Synchronization success: $s")
            case f: SynchronizationResult.SynchronizationFailure =>
              log.error(s"Synchronization failure: $f", f.throwable)
          }
      }

  def synchronizeAccount(account: Account, previousBlockState: Option[Block])(implicit cs: ContextShift[IO], t: Timer[IO], lc: LamaLogContext): IO[SynchronizationResult] = {

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
          ChangeType.Internal,
          None
        ) ++ bookkeeper
        .record[UnconfirmedTransaction](
          account.coin,
          account.id,
          keychainId,
          ChangeType.External,
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
        .evalMap(b => b.map(rewindToLastValidBlock(account, _, args.syncId)).sequence)
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
        args.syncId,
        (addressesUsed ++ addressesUsedByMempool).distinct
      )

      _ <- log.info(s"$opsCount operations computed")

    } yield {
      // Create the reportable successful event.
      SynchronizationResult.SynchronizationSuccess(args, lastMinedBlock.block)
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

  private def getOrCreateAccount(xpub: String): IO[Account] = ???
}
