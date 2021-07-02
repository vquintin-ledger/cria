package co.ledger.cria.domain.models.interpreter

import co.ledger.cria.domain.models.account.AccountId

case class AccountTxView(accountId: AccountId, tx: TransactionView)
