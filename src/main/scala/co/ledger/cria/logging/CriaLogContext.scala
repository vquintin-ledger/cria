package co.ledger.cria.logging

import co.ledger.cria.domain.models.account.{Account, AccountUid}
import co.ledger.cria.domain.models.interpreter.{Coin, SyncId}
import co.ledger.cria.domain.models.keychain.KeychainId

case class CriaLogContext(
    accountId: Option[AccountUid] = None,
    identifier: Option[KeychainId] = None,
    coin: Option[Coin] = None,
    correlationId: Option[SyncId] = None,
    customFields: List[Option[(String, String)]] = List()
) extends LogContext {

  def withAccount(account: Account): CriaLogContext =
    copy(
      accountId = Some(account.accountUid),
      identifier = Some(account.identifier)
    )

  def withAccountId(accountId: AccountUid): CriaLogContext =
    copy(accountId = Some(accountId))

  def withIdentifier(identifier: KeychainId): CriaLogContext =
    copy(identifier = Some(identifier))

  def withCoin(coin: Coin): CriaLogContext =
    copy(coin = Some(coin))

  def withCorrelationId(correlationId: SyncId): CriaLogContext =
    copy(correlationId = Some(correlationId))

  def withCustomField(key: String, value: String): CriaLogContext =
    copy(customFields = Some((key, value)) :: customFields)

  override def asMap(): Map[String, String] = (List(
    accountId.map(account => ("id", account.toString)),
    identifier.map(id => ("identifier", id.toString)),
    coin.map(c => ("coin", c.name)),
    correlationId.map(id => ("correlation_id", id.toString))
  ) ::: customFields).flatten.toMap

}
