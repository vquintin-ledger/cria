package co.ledger.cria.clients.explorer.models

import io.circe.generic.extras.Configuration

object circeImplicits {

  implicit val defaultCirceConfig: Configuration =
    Configuration.default.withSnakeCaseConstructorNames.withSnakeCaseMemberNames

}
