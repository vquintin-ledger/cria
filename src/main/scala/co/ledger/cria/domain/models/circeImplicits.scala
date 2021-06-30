package co.ledger.cria.domain.models

import io.circe.generic.extras.Configuration

object circeImplicits {

  implicit val defaultCirceConfig: Configuration =
    Configuration.default.withSnakeCaseConstructorNames.withSnakeCaseMemberNames

}
