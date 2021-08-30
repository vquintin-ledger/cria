package co.ledger.cria.domain.adapters.persistence.wd.models

import cats.implicits._
import co.ledger.cria.domain.models.interpreter.{OutputView, Satoshis}

case class WDOutput(
    idx: Int,
    amount: Satoshis,
    script: String,
    address: String,
    accountUid: Option[String],
    blockHeight: Option[Long],
    replaceable: Int = 0
)

object WDOutput {

  def fromOutput(output: OutputView, accountId: String, block: Option[WDBlock]) =
    WDOutput(
      output.outputIndex,
      amount = output.value,
      script = output.scriptHex,
      address = output.address,
      accountUid = output.derivation.as(accountId),
      blockHeight = block.map(_.height)
    )

}
