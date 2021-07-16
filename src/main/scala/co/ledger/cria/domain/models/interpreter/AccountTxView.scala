package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.account.AccountUid

case class AccountTxView(accountId: AccountUid, tx: TransactionView)
