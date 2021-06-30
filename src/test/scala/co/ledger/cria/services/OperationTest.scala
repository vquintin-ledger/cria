package co.ledger.cria.services

import co.ledger.cria.models.account.AccountId

import java.util.UUID
import co.ledger.cria.models.interpreter.{Operation, OperationType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OperationTest extends AnyFlatSpec with Matchers {

  "Operation.uid" should "be a SHA-256 digest of (accountId,txId,operationType )" in {

    val accountId = AccountId(UUID.fromString("3bd0b597-3638-4167-9ed3-aa4267efbe0c"))
    val txId      = Operation.TxId("169e1e83e930853391bc6f35f605c6754cfead57cf8387639d3b4096c54f18f4")
    val uid       = Operation.uid(accountId, txId, OperationType.Receive, None)

    uid should be(
      Operation.UID("0135af8e341fb691ba8e4f611635e46637aa9bdfed84d01f1040d180ed3a166386cd1")
    )

    uid should not be Operation.uid(accountId, txId, OperationType.Send, None)
    uid should not be Operation.uid(
      AccountId(UUID.randomUUID()),
      txId,
      OperationType.Receive,
      None
    )
    uid should not be Operation.uid(
      accountId,
      Operation.TxId("another tx id"),
      OperationType.Receive,
      None
    )
  }

}
