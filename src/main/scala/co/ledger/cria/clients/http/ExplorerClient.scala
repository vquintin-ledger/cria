package co.ledger.cria.clients.http

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.cria.models.explorer.{
  Block,
  ConfirmedTransaction,
  Transaction,
  UnconfirmedTransaction
}
import co.ledger.cria.clients.Exceptions.ExplorerClientException
import co.ledger.cria.clients.http.ExplorerClient.Address
import co.ledger.cria.config.ExplorerConfig
import co.ledger.cria.models.explorer._
import co.ledger.cria.logging.{ContextLogging, CriaLogContext}
import co.ledger.cria.models.account.Coin
import co.ledger.cria.models.circeImplicits._
import co.ledger.cria.utils
import co.ledger.cria.utils.IOUtils
import fs2.{Chunk, Pull, Stream}
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.client.Client
import org.http4s.{EntityDecoder, Method, Request, Uri}

import scala.concurrent.duration._

case class TransactionHex(transactionHash: String, hex: String)

object TransactionHex {
  implicit val encoder: Encoder[TransactionHex] = deriveConfiguredEncoder[TransactionHex]
  implicit val decoder: Decoder[TransactionHex] = deriveConfiguredDecoder[TransactionHex]
}

trait ExplorerClient {

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block]

  def getBlock(hash: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Block]]

  def getBlock(height: Long)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block]

  def getConfirmedTransactions(
      addresses: Seq[Address],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, ConfirmedTransaction]

  def getUnconfirmedTransactions(
      addresses: Set[Address]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, UnconfirmedTransaction]

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
    case Coin.Btc        => "/blockchain/v3/btc"
    case Coin.BtcTestnet => "/blockchain/v3/btc_testnet"
    case Coin.BtcRegtest => "/blockchain/v3/btc_regtest"
    case Coin.Ltc        => "/blockchain/v3/ltc"
  }

  private def callExpect[A](
      uri: Uri
  )(implicit d: EntityDecoder[IO, A], t: Timer[IO], lc: CriaLogContext): IO[A] =
    for {
      _ <- log.debug(s"Calling explorer with uri : ${uri.toString()}")
      response <- IOUtils.withTimer(s"Call explorer on : ${uri.toString()}")(
        httpClient
          .expect[A](uri)
          .handleErrorWith(e => IO.raiseError(ExplorerClientException(uri, e)))
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
          .handleErrorWith(e => IO.raiseError(ExplorerClientException(req.uri, e)))
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
        val explorerException = ExplorerClientException(req.uri, e)
        log.error("Explorer error", explorerException) *>
          IO.raiseError(explorerException)
      }

  def getCurrentBlock(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block] =
    callExpect[Block](conf.uri.withPath(s"$coinBasePath/blocks/current"))

  def getBlock(hash: String)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Option[Block]] =
    callExpect[List[Block]](conf.uri.withPath(s"$coinBasePath/blocks/$hash"))
      .map(_.headOption)

  def getBlock(height: Long)(implicit lc: CriaLogContext, t: Timer[IO]): IO[Block] =
    callExpect[Block](conf.uri.withPath(s"$coinBasePath/blocks/$height"))

  def getConfirmedTransactions(
      addresses: Seq[Address],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, ConfirmedTransaction] =
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
            // The explorer v3 returns also unconfirmed txs, so we need to remove it
            val confirmedTxs = res.txs.collect { case confirmedTx: ConfirmedTransaction =>
              confirmedTx
            }
            Stream.emits(confirmedTxs)
          }
      }
      .parJoinUnbounded

  def getUnconfirmedTransactions(
      addresses: Set[Address]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      lc: CriaLogContext
  ): Stream[IO, UnconfirmedTransaction] = {

    val getPendingTransactionRequest = (as: Chunk[Address]) => {
      val baseUri = conf.uri
        .withPath(s"$coinBasePath/addresses/${as.toList.mkString(",")}/transactions/pending")
        .withQueryParam("no_token", true)
      Request[IO](Method.GET, baseUri)
    }

    Stream
      .emits(addresses.toSeq)
      .chunkN(conf.addressesSize)
      .evalTap(addresses =>
        log.info(s"Getting pending txs for ${addresses.size} addresses") *>
          log.debug(s"addresses: ${addresses.toList.mkString(",")}")
      )
      .map(getPendingTransactionRequest)
      .evalMap(request => callExpectWithRetry[List[UnconfirmedTransaction]](request))
      .flatMap(Stream.emits(_))
  }

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
        .withQueryParam("no_token", true)
        .withQueryParam("batch_size", conf.txsBatchSize)

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
              .collect { case confirmedTx: ConfirmedTransaction =>
                confirmedTx
              }
              .maxByOption(_.block.time)
              .map(_.block.hash)

          Pull.output(Chunk(res)) >>
            fetchPaginatedTransactions(addresses, lastBlockHash)
        } else {
          Pull.output(Chunk(res))
        }
      }
}
