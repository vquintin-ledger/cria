package co.ledger.cria.clients.explorer.v2

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.clients.explorer.v2
import co.ledger.cria.clients.explorer.v2.ExplorerClient.Address
import co.ledger.cria.clients.explorer.v2.models._
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.utils
import co.ledger.cria.utils.IOUtils
import fs2.{Chunk, Pull, Stream}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder, Json}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Method, Request, Uri}
import models.circeImplicits._

import scala.concurrent.duration._

case class TransactionHex(transactionHash: String, hex: String)

object TransactionHex {
  implicit val encoder: Encoder[TransactionHex] = deriveConfiguredEncoder[TransactionHex]
  implicit val decoder: Decoder[TransactionHex] = deriveConfiguredDecoder[TransactionHex]
}

trait ExplorerClient {

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block]

  def getBlock(height: Long)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block]

  def getTransactions(
      addresses: Seq[Address],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, Transaction]

  def broadcastTransaction(tx: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[String]

  def getRawTransactionHex(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[String]

  def getTransaction(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Transaction]]
}

object ExplorerClient {
  type Address = String
}

class ExplorerHttpClient(httpClient: Client[IO], conf: ExplorerConfig, coin: Coin)
    extends ExplorerClient
    with ContextLogging {

  private val coinBasePath = coin match {
    case Coin.Btc        => "/blockchain/v2/btc"
    case Coin.BtcTestnet => "/blockchain/v2/btc_testnet"
    case Coin.BtcRegtest => "/blockchain/v2/btc_regtest"
    case Coin.Ltc        => "/blockchain/v2/ltc"
  }

  private def callExpect[A](
      uri: Uri
  )(implicit d: EntityDecoder[IO, A], t: Timer[IO], lc: CriaLogContext): IO[A] =
    for {
      _ <- log.debug(s"Calling explorer with uri : ${uri.toString()}")
      response <- IOUtils.withTimer(s"Call explorer on : ${uri.toString()}")(
        httpClient
          .expect[A](uri)
          .handleErrorWith(e => IO.raiseError(v2.ExplorerClientException(uri, e)))
      )
    } yield response

  private def callExpect[A](
      req: Request[IO]
  )(implicit d: EntityDecoder[IO, A], t: Timer[IO], lc: CriaLogContext): IO[A] =
    for {
      _ <- log.debug(s"Call explorer with request : ${req.toString()}")
      response <- IOUtils.withTimer(s"Call explorer on : ${req.uri.toString()}")(
        httpClient
          .expect[A](req)
          .handleErrorWith(e => IO.raiseError(v2.ExplorerClientException(req.uri, e)))
      )
    } yield response

  private def callExpectWithRetry[A](
      req: Request[IO]
  )(implicit
      cs: ContextShift[IO],
      d: EntityDecoder[IO, A],
      t: Timer[IO],
      lc: CriaLogContext
  ): IO[A] =
    IOUtils
      .retry(
        callExpect(req).timeout(conf.timeout),
        policy = utils.RetryPolicy.exponential(initial = 500.millis, maxElapsedTime = 30.seconds)
      )
      .handleErrorWith { e =>
        val explorerException = v2.ExplorerClientException(req.uri, e)
        log.error("Explorer error", explorerException) *>
          IO.raiseError(explorerException)
      }

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block] =
    callExpect[Block](conf.uri.withPath(s"$coinBasePath/blocks/current"))

  def getBlock(height: Long)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block] =
    callExpect[Block](conf.uri.withPath(s"$coinBasePath/blocks/$height"))

  def getTransactions(
      addresses: Seq[Address],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, Transaction] =
    Stream
      .emits(addresses)
      .chunkN(conf.addressesSize)
      .evalTap(addressChunk =>
        log.info(s"Fetching chunk of ${addressChunk.size} addresses") *>
          log.debug(s"addresses: ${addressChunk.toList.mkString(",")}")
      )
      .map { chunk =>
        fetchPaginatedTransactions(chunk.toList, blockHash).stream
          .flatMap { res =>
            // The explorer v2 returns also unconfirmed txs, so we need to remove it
            val confirmedTxs = res.txs.collect { case confirmedTx: Transaction =>
              confirmedTx
            }
            Stream.emits(confirmedTxs)
          }
      }
      .parJoinUnbounded

  def getRawTransactionHex(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[String] =
    for {
      rawResponse <- callExpect[List[TransactionHex]](
        conf.uri.withPath(s"$coinBasePath/transactions/$transactionHash/hex")
      )
      hex <- IO.fromOption(rawResponse.headOption.map(_.hex))(new Exception(""))
    } yield hex

  def getTransaction(
      transactionHash: String
  )(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Transaction]] =
    for {
      rawResponse <- callExpect[Option[Transaction]](
        conf.uri.withPath(s"$coinBasePath/transactions/$transactionHash")
      )
    } yield rawResponse

  def broadcastTransaction(tx: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[String] =
    callExpect[SendTransactionResult](
      Request[IO](
        Method.POST,
        conf.uri.withPath(s"$coinBasePath/transactions/send")
      ).withEntity(Json.obj("tx" -> Json.fromString(tx)))
    ).map(_.result)

  private def GetOperationsRequest(addresses: Seq[String], blockHash: Option[String]) = {
    val baseUri =
      conf.uri
        .withPath(s"$coinBasePath/addresses/${addresses.mkString(",")}/transactions")
        .withQueryParam("noToken", true)
        .withQueryParam("batchSize", conf.txsBatchSize)

    Request[IO](
      Method.GET,
      blockHash match {
        case Some(value) => baseUri.withQueryParam("block_hash", value)
        case None        => baseUri
      }
    )

  }

  private def fetchPaginatedTransactions(
      addresses: Seq[String],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      decoder: Decoder[GetTransactionsResponse],
      lc: CriaLogContext
  ): Pull[IO, GetTransactionsResponse, Unit] =
    Pull
      .eval(
        log.info(s"Blockchain tx pagination at block_hash=$blockHash") *>
          callExpectWithRetry[GetTransactionsResponse](GetOperationsRequest(addresses, blockHash))
      )
      .flatMap { res =>
        if (res.truncated) {
          // Txs are not sorted per page,
          // so we need get only confirmed txs and
          // get the most recent fetched block hash for the next cursor
          val lastBlockHash =
            res.txs
              .filter(_.block.isDefined)
              .maxByOption(_.block.get.time)
              .map(_.block.get.hash)

          Pull.output(Chunk(res)) >>
            fetchPaginatedTransactions(addresses, lastBlockHash)
        } else {
          Pull.output(Chunk(res))
        }
      }
}
