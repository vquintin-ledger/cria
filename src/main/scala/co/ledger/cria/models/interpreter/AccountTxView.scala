package co.ledger.cria.models.interpreter

import co.ledger.cria.models.account.AccountId

case class AccountTxView(accountId: AccountId, tx: TransactionView)
