package co.ledger.cria.itutils.models

import co.ledger.cria.domain.models.interpreter.Operation

case class GetOperationsResult(
    operations: List[Operation],
    total: Int,
    cursor: Option[PaginationCursor]
)
