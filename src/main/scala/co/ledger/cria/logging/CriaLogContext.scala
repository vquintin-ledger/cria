package co.ledger.cria.logging

import java.util.UUID

import co.ledger.cria.models.account.{Account, Coin, CoinFamily}

case class CriaLogContext(
    accountId: Option[UUID] = None,
    identifier: Option[String] = None,
    coinFamily: Option[CoinFamily] = None,
    coin: Option[Coin] = None,
    correlationId: Option[UUID] = None,
    customFields: List[Option[(String, String)]] = List()
) extends LogContext {

  def withAccount(account: Account): CriaLogContext =
    copy(
      accountId = Some(account.id),
      identifier = Some(account.identifier),
      coinFamily = Some(account.coinFamily)
    )

  def withAccountId(accountId: UUID): CriaLogContext =
    copy(accountId = Some(accountId))

  def withIdentifier(identifier: String): CriaLogContext =
    copy(identifier = Some(identifier))

  def withCoinFamily(coinFamily: CoinFamily): CriaLogContext =
    copy(coinFamily = Some(coinFamily))

  def withCoin(coin: Coin): CriaLogContext =
    copy(coin = Some(coin))

  def withCorrelationId(correlationId: UUID): CriaLogContext =
    copy(correlationId = Some(correlationId))

  def withCustomField(key: String, value: String): CriaLogContext =
    copy(customFields = Some((key, value)) :: customFields)

  override def asMap(): Map[String, String] = (List(
    accountId.map(account => ("id", account.toString)),
    identifier.map(id => ("identifier", id)),
    coinFamily.map(cf => ("coin_family", cf.name)),
    coin.map(c => ("coin", c.name)),
    correlationId.map(id => ("correlation_id", id.toString))
  ) ::: customFields).flatten.toMap

}
