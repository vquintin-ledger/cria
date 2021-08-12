package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.TxHash
import org.scalatest.funsuite.AnyFunSuite
import cats.implicits._
import java.time.Instant

class TransactionViewTest extends AnyFunSuite {

  test("Can make a valid transaction") {
    val tx = makeTx(
      fees = 300,
      List(input0, input1),
      List(output0, output1)
    )

    assert(tx.isRight)
  }

  test("Can't have non-matching fees in transaction") {

    val tx = makeTx(
      fees = 500, // Should be 300
      List(input0, input1),
      List(output0, output1)
    )

    assert(tx.isLeft)
  }

  test("Can't have negative fees in transaction") {

    val tx = makeTx(
      fees = -1700, // Should not be negative
      List(input0),
      List(output0, output1)
    )

    assert(tx.isLeft)
  }

  test("Can't have input in double") {

    val tx = makeTx(
      fees = 1100,
      List(input0, input0), // Can't have input with index 0 twice
      List(output0)
    )

    assert(tx.isLeft)
  }

  test("Can't have output in double") {

    val tx = makeTx(
      fees = 1200,
      List(input0, input1),
      List(output0, output0) // Output at index 0 is there twice
    )

    assert(tx.isLeft)
  }

  test("Can't have hole in inputs") {
    val input2 = input1.copy(inputIndex = 2)

    val tx = makeTx(
      fees = 300,
      List(input0, input2), // No input at index 1
      List(output0, output1)
    )

    assert(tx.isLeft)
  }

  test("Can't have hole in outputs") {
    val output2 = output1.copy(outputIndex = 2)

    val tx = makeTx(
      fees = 300,
      List(input0, input1),
      List(output0, output2) // No output at index 2
    )

    assert(tx.isLeft)
  }

  private def makeTx(
      fees: BigInt,
      inputs: List[InputView],
      outputs: List[OutputView]
  ): Either[Throwable, TransactionView] =
    TransactionView.asMonadError[Either[Throwable, *]](
      id = "tx",
      hash =
        TxHash.fromStringUnsafe("edc8e160364384332a2c95dd17d0d66612d53a8711739416d6372e75474d40ab"),
      receivedAt = Instant.now(),
      lockTime = 0,
      fees = fees,
      inputs = inputs,
      outputs = outputs, // No output at index 2
      None,
      0
    )

  private lazy val input0 = InputView(
    TxHash.fromStringUnsafe("c569d424111eaa8d66c7a1124c203c65649197b9855e54563dfc2799da928c1c"),
    0,
    0,
    1000,
    "address0",
    "SIG",
    Nil,
    0,
    None
  )

  private lazy val input1 = InputView(
    TxHash.fromStringUnsafe("c849117bf6203c900e649f46bb35f5e8b939c5cc89cc39086c7c09ff202acda3"),
    0,
    1,
    2000,
    "address1",
    "SIG",
    Nil,
    0,
    None
  )

  private lazy val output0 = OutputView(
    0,
    900,
    "out_address_0",
    "script0",
    None,
    None
  )

  private lazy val output1 = OutputView(
    1,
    1800,
    "out_address_1",
    "script0",
    None,
    None
  )
}
