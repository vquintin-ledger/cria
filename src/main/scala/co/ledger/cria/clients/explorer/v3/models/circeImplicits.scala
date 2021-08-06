package co.ledger.cria.clients.explorer.v3.models

import io.circe.generic.extras.Configuration

object circeImplicits {

  implicit val defaultCirceConfig: Configuration =
    Configuration.default.withSnakeCaseConstructorNames.withSnakeCaseMemberNames

}
