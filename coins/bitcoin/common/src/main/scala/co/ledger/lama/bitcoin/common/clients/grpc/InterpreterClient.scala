package co.ledger.lama.bitcoin.common.clients.grpc

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.common.utils.BtcProtoUtils._
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.clients.grpc.GrpcClient
import co.ledger.lama.common.models.Account
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.{ManagedChannel, Metadata}
import fs2._

trait InterpreterClient {
  def saveTransactions(accountId: UUID): Pipe[IO, TransactionView, Unit]

  def removeDataFromCursor(
      accountId: UUID,
      blockHeightCursor: Option[Long],
      followUpId: UUID
  ): IO[Int]

  def getLastBlocks(accountId: UUID): IO[List[BlockView]]

  def compute(
      account: Account,
      syncId: UUID,
      addresses: List[AccountAddress]
  ): IO[Int]

}

class InterpreterGrpcClient(
    val managedChannel: ManagedChannel
)(implicit val cs: ContextShift[IO])
    extends InterpreterClient {

  val client: protobuf.BitcoinInterpreterServiceFs2Grpc[IO, Metadata] =
    GrpcClient.resolveClient(
      protobuf.BitcoinInterpreterServiceFs2Grpc.stub[IO],
      managedChannel,
      "InterpreterClient"
    )

  def saveTransactions(accountId: UUID): Pipe[IO, TransactionView, Unit] =
    _.map { tx =>
      protobuf.SaveTransactionRequest(
        accountId = UuidUtils.uuidToBytes(accountId),
        transaction = Some(tx.toProto)
      )
    }.through(client.saveTransactions(_, new Metadata()).as(()))

  def removeDataFromCursor(
      accountId: UUID,
      blockHeightCursor: Option[Long],
      followUpId: UUID
  ): IO[Int] =
    client
      .removeDataFromCursor(
        protobuf.DeleteTransactionsRequest(
          UuidUtils uuidToBytes accountId,
          blockHeightCursor.getOrElse(0),
          UuidUtils.uuidToBytes(followUpId)
        ),
        new Metadata()
      )
      .map(_.count)

  def getLastBlocks(accountId: UUID): IO[List[BlockView]] =
    client
      .getLastBlocks(
        protobuf.GetLastBlocksRequest(
          UuidUtils uuidToBytes accountId
        ),
        new Metadata()
      )
      .map(_.blocks.map(BlockView.fromProto).toList)

  def compute(
      account: Account,
      syncId: UUID,
      addresses: List[AccountAddress]
  ): IO[Int] =
    client
      .compute(
        protobuf.ComputeRequest(
          Some(account.toBtcProto),
          UuidUtils.uuidToBytes(syncId),
          addresses.map(_.toProto)
        ),
        new Metadata()
      )
      .map(_.count)

}
