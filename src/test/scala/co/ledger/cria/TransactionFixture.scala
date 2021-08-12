package co.ledger.cria

import cats.data.NonEmptyList
import cats.implicits.toFoldableOps
import co.ledger.cria.clients.explorer.v3.ExplorerClient.Address
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.interpreter.{
  BlockView,
  Confirmation,
  InputView,
  OutputView,
  TransactionView
}
import co.ledger.cria.utils.HexUtils
import shapeless.tag
import shapeless.tag.@@

import java.time.Instant
import scala.util.Random

object TransactionFixture {

  def output(address: Address) = OutputView(
    outputIndex = 0,
    value = 1L,
    address,
    scriptHex = "",
    None,
    None
  )

  def input(address: Address): InputView = InputView(
    outputHash =
      TxHash.fromStringUnsafe("0eb14f1817ad99446764f190479b8e7f987127113ab3af1504dc4603655f987a"),
    outputIndex = 0,
    inputIndex = 0,
    value = 1L,
    address = address,
    scriptSignature = "",
    txinwitness = List.empty,
    sequence = 0L,
    None
  )

  def transfer(fromAddress: Address): TransactionView @@ Confirmation.Unconfirmed =
    unconfirmedTransaction(
      inputs = NonEmptyList.one(input(fromAddress)),
      outputs = NonEmptyList.one(output(s"dest-${Random.nextInt(8)})"))
    )

  def receive(toAddress: Address): TransactionView @@ Confirmation.Unconfirmed =
    unconfirmedTransaction(
      inputs = NonEmptyList.one(input(s"sender-${Random.nextInt(8)}")),
      outputs = NonEmptyList.one(output(toAddress))
    )

  object confirmed {

    def transfer(
        fromAddress: Address,
        inBlock: BlockView
    ): TransactionView @@ Confirmation.Confirmed =
      confirmedTransaction(
        inputs = NonEmptyList.one(input(fromAddress)),
        outputs = NonEmptyList.one(output(s"dest-${Random.nextInt(8)})")),
        inBlock
      )

    def receive(toAddress: Address, inBlock: BlockView): TransactionView @@ Confirmation.Confirmed =
      confirmedTransaction(
        inputs = NonEmptyList.one(input(s"sender-${Random.nextInt(8)}")),
        outputs = NonEmptyList.one(output(toAddress)),
        inBlock
      )

  }

  def unconfirmedTransaction(
      inputs: NonEmptyList[InputView],
      outputs: NonEmptyList[OutputView]
  ): TransactionView @@ Confirmation.Unconfirmed = tag[Confirmation.Unconfirmed](
    TransactionView.unsafe(
      id = s"id-${Random.nextInt(100)}",
      hash = randomTxHash(),
      receivedAt = Instant.now(),
      lockTime = 0L,
      fees = inputs.foldMap(_.value) - outputs.foldMap(_.value),
      inputs = inputs.toList,
      outputs = outputs.toList,
      None,
      confirmations = 1
    )
  )

  def confirmedTransaction(
      inputs: NonEmptyList[InputView],
      outputs: NonEmptyList[OutputView],
      block: BlockView
  ): TransactionView @@ Confirmation.Confirmed = tag[Confirmation.Confirmed](
    TransactionView.unsafe(
      id = s"id-${Random.nextInt(100) + 100}",
      hash = randomTxHash(),
      receivedAt = Instant.now(),
      lockTime = 0L,
      fees = inputs.foldMap(_.value) - outputs.foldMap(_.value),
      inputs = inputs.toList,
      outputs = outputs.toList,
      confirmations = 1,
      block = Some(block)
    )
  )

  def randomTxHash(): TxHash =
    TxHash.fromStringUnsafe(HexUtils.valueOf(Random.nextBytes(32)))
}
