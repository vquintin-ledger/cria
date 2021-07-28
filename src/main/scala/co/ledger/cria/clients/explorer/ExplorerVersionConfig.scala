package co.ledger.cria.clients.explorer

import co.ledger.cria.clients.explorer.models.ExplorerConfig

case class ExplorerVersionConfig private (config: ExplorerConfig, noToken: String, blockHash: String)

object ExplorerVersionConfig {
  def fromConfig(config: ExplorerConfig): Either[String, ExplorerVersionConfig] = {
    val path = config.uri.path
    if (path.contains("v2"))
      Right(makeV2Config(config))
    else if (path.contains("v3"))
      Right(makeV3Config(config))
    else
      Left("Could not guess if v2 or v3")
  }

  private def makeV2Config(config: ExplorerConfig): ExplorerVersionConfig =
    new ExplorerVersionConfig(
      config = config,
      noToken = "noToken",
      blockHash = "blockHash"
    )

  private def makeV3Config(config: ExplorerConfig): ExplorerVersionConfig =
    new ExplorerVersionConfig(
      config = config,
      noToken = "no_token",
      blockHash = "block_hash"
    )
}
