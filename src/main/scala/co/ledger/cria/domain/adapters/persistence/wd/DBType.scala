package co.ledger.cria.domain.adapters.persistence.wd

sealed abstract class DBType

object DBType {
  sealed abstract class WD        extends DBType
  sealed abstract class Temporary extends DBType
}
