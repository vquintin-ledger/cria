package co.ledger.cria.logging

trait DefaultContextLogging extends ContextLogging {
  implicit val lc: LamaLogContext = LamaLogContext()
}
