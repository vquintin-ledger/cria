package co.ledger.cria.domain.models.account

case class Address(value: String) extends AnyVal
case class InvalidAddress(address: Address, reason: String)
