package co.ledger.cria.itutils.queries

import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.logging.DoobieLogHandler
import doobie._
import doobie.implicits._

object AccountTestQueries extends DoobieLogHandler {

  def addAccount(accountId: AccountUid, walletUid: WalletUid): ConnectionIO[Int] = {
    val wallet =
      sql"""INSERT INTO
          wallets (uid, name, currency_name, pool_name, configuration, created_at)
          VALUES (
          $walletUid,
          'test-wallet',
          'bitcoin',
          'test-pool',
          '',
          ''
          )
       """.update.run

    val account =
      sql"""INSERT INTO
         accounts (uid, idx, wallet_uid, created_at)
         VALUES (
         $accountId,
         0,
         $walletUid,
         ''
         )
         """.update.run

    wallet.flatMap(_ => account)

  }

}
