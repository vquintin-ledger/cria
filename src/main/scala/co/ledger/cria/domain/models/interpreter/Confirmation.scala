package co.ledger.cria.domain.models.interpreter

sealed abstract class Confirmation

object Confirmation {
  final abstract class Confirmed   extends Confirmation
  final abstract class Unconfirmed extends Confirmation
}
