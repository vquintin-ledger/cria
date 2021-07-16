package co.ledger.cria.domain.adapters.wd.models

import java.math.BigInteger
import java.security.MessageDigest

import co.ledger.cria.domain.models.interpreter.InputView

case class WDInput(
    uid: String,
    previousOutputIdx: Int,
    previousTxHash: String,
    previousTxUid: String,
    amount: BigInt,
    inputIndex: Int,
    address: String,
    coinbase: Option[String],
    sequence: Int
)

object WDInput {

  val digester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def fromInput(input: InputView, accountId: String): WDInput =
    WDInput(
      uid = createUid(
        accountUid = accountId,
        outputIndex = input.outputIndex,
        previousTxHash = input.outputHash,
        coinbase = None
      ),
      previousOutputIdx = input.outputIndex,
      previousTxHash = input.outputHash,
      previousTxUid = WDTransaction.createUid(accountId, input.outputHash),
      amount = input.value,
      inputIndex = input.inputIndex,
      address = input.address,
      coinbase = None,
      sequence = input.sequence.toInt
    )

  def createUid(
      accountUid: String,
      outputIndex: Int,
      previousTxHash: String,
      coinbase: Option[String]
  ) = {
    String.format(
      "%064x",
      new BigInteger(
        1,
        digester.digest(
          s"uid:$accountUid+$outputIndex+$previousTxHash+${coinbase.getOrElse("")}"
            .getBytes("UTF-8")
        )
      )
    )
  }
}
