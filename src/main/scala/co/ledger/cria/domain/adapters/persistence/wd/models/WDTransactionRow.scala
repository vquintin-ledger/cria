package co.ledger.cria.domain.adapters.persistence.wd.models

import co.ledger.cria.domain.models.TxHash
import co.ledger.cria.domain.models.interpreter.{BlockHash, BlockHeight}
import doobie.Read
import doobie.postgres.implicits._
import co.ledger.cria.domain.models.implicits._
import co.ledger.cria.domain.adapters.persistence.wd.queries.WDQueryImplicits._

import java.time.Instant

case class WDTransactionRow(
    id: String,
    hash: TxHash,
    blockHash: Option[BlockHash],
    blockHeight: Option[BlockHeight],
    blockTime: Option[Instant],
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    confirmations: Int
)

object WDTransactionRow {
  implicit lazy val readTransactionView: Read[WDTransactionRow] = {
    Read[
      (
          String,
          TxHash,
          Option[BlockHash],
          Option[BlockHeight],
          Option[Instant],
          Instant,
          Long,
          BigInt,
          Int
      )
    ].map {
      case (
            id,
            hash,
            blockHashO,
            blockHeightO,
            blockTimeO,
            receivedAt,
            lockTime,
            fees,
            confirmations
          ) =>
        WDTransactionRow(
          id = id,
          hash = hash,
          blockHash = blockHashO,
          blockHeight = blockHeightO,
          blockTime = blockTimeO,
          receivedAt = receivedAt,
          lockTime = lockTime,
          fees = fees,
          confirmations = confirmations
        )
    }
  }
}
