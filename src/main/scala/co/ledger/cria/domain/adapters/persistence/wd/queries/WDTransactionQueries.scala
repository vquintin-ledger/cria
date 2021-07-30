package co.ledger.cria.domain.adapters.persistence.wd.queries

import co.ledger.cria.logging.DoobieLogHandler
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.BlockView
import co.ledger.cria.domain.models.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import fs2._

object WDTransactionQueries extends DoobieLogHandler {

  def fetchMostRecentBlocks(accountId: AccountUid): Stream[ConnectionIO, BlockView] = {
    sql"""SELECT DISTINCT b.hash, b.height, b."time"::timestamp
          FROM blocks b
          INNER JOIN operations op
            ON b.uid = op.block_uid
          WHERE op.account_uid = $accountId
          ORDER BY b.height DESC
          LIMIT 200 -- the biggest reorg that happened on bitcoin was 53 blocks long
       """.query[BlockView].stream
  }
}
