package co.ledger.cria.logging

trait LogContext {
  def asMap(): Map[String, String]
}
