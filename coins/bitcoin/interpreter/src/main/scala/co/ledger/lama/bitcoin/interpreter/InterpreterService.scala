package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.common.utils.BtcProtoUtils._
import co.ledger.lama.bitcoin.interpreter.models.AccountTxView
import co.ledger.lama.bitcoin.interpreter.protobuf.SaveTransactionRequest
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.{Metadata, ServerServiceDefinition}
import fs2.Stream

trait InterpreterService extends protobuf.BitcoinInterpreterServiceFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    protobuf.BitcoinInterpreterServiceFs2Grpc.bindService(this)
}

class InterpreterGrpcService(
    interpreter: Interpreter
) extends InterpreterService {

  def saveTransactions(
      request: Stream[IO, SaveTransactionRequest],
      ctx: Metadata
  ): Stream[IO, com.google.protobuf.empty.Empty] =
    request
      .evalMap { r =>
        for {
          accountId <- UuidUtils.bytesToUuidIO(r.accountId)
          tx <- IO
            .fromOption(r.transaction)(new IllegalArgumentException("Missing transaction to save"))
            .map(TransactionView.fromProto)
        } yield AccountTxView(accountId, tx)
      }
      .through(interpreter.saveTransactions)
      .as(com.google.protobuf.empty.Empty())

  def getLastBlocks(
      request: protobuf.GetLastBlocksRequest,
      ctx: Metadata
  ): IO[protobuf.GetLastBlocksResult] = {
    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      blocks    <- interpreter.getLastBlocks(accountId)
    } yield protobuf.GetLastBlocksResult(blocks.map(_.toProto))
  }

  def removeDataFromCursor(
      request: protobuf.DeleteTransactionsRequest,
      ctx: Metadata
  ): IO[protobuf.ResultCount] = {
    for {
      accountId  <- UuidUtils.bytesToUuidIO(request.accountId)
      followUpId <- UuidUtils.bytesToUuidIO(request.followUpId)
      blockHeight = request.blockHeight
      txRes <- interpreter.removeDataFromCursor(accountId, blockHeight, followUpId)
    } yield protobuf.ResultCount(txRes)
  }

  def compute(
      request: protobuf.ComputeRequest,
      ctx: Metadata
  ): IO[protobuf.ResultCount] =
    for {
      account   <- IO(BtcAccount.fromBtcProto(request.account.get))
      syncId    <- UuidUtils.bytesToUuidIO(request.syncId)
      addresses <- IO(request.addresses.map(AccountAddress.fromProto).toList)
      nbOps     <- interpreter.compute(account, syncId, addresses)
    } yield protobuf.ResultCount(nbOps)

}
