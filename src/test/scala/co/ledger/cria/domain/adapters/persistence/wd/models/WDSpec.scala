package co.ledger.cria.domain.adapters.persistence.wd.models

import cats.data.NonEmptyList
import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockHash, Coin, OperationType, OutputView}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WDSpec extends AnyFlatSpec with Matchers {

  "an operation uid" should "compute same uid as WD" in {

    val uid = WDOperation.computeUid(
      accountUid = AccountUid("c65cb458dac2dcf77a1136088c41a9e64ab4f03be08d45939bef09ee9ea01b61"),
      txHash =
        TxHash.fromStringUnsafe("7d9a76857a08753856bccfd64fc090281b381fadeb1a47be1218f6d00d07f669"),
      operationType = OperationType.Receive
    )

    uid shouldBe "9c058e92840f8c8eaac36d3b77b67515295b55d6ae20ba2285569bef656aba6d"

  }

  "a hash" should "have 64 characters" in {

    val uid = WDBlock.computeUid(
      BlockHash.fromStringUnsafe(
        "00000000ce666ed94ff2653a52d58939e7b303eca70300e649427d1fac095baf"
      ),
      Coin.BtcTestnet
    )

    uid shouldBe "0438e4de10ff47c097f792dd6683b1997e2870dd6b3f25d6de5bd26a2a56c4d3"

  }

  "an amount" should "be written in hexa with a padding 0" in {
    WDOperation.toHexString(BigInt(1)) shouldBe "01"
    WDOperation.toHexString(BigInt(3 + 16 * 16)) shouldBe "0103"
  }

  "an amount" should "have net value depending on operation type" in {
    WDOperation.netAmount(OperationType.Send, BigInt(30), BigInt(10)) shouldBe 20
    WDOperation.netAmount(OperationType.Receive, BigInt(30), BigInt(10)) shouldBe 30
  }

  "an operations recipients" should "depend on the operationType" in {
    val belongAddess     = "address1"
    val notBelongAddress = "address2"
    val belongOutput     = OutputView(0, 0, belongAddess, "", None, Some(NonEmptyList(1, List(0))))
    val notBelongOutput  = OutputView(0, 0, notBelongAddress, "", None, None)
    WDOperation.getRecipients(
      OperationType.Receive,
      List(belongOutput, notBelongOutput)
    ) shouldBe Seq(belongAddess, notBelongAddress)
    WDOperation.getRecipients(
      OperationType.Send,
      List(belongOutput, notBelongOutput)
    ) shouldBe Seq(notBelongAddress)
  }
}
