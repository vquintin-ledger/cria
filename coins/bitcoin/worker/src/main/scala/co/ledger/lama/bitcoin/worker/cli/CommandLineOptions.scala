package co.ledger.lama.bitcoin.worker.cli

import com.monovore.decline._
import cats.implicits._

import java.util.UUID

case class CommandLineOptions(xpub: String,
                              syncId: UUID,
                              cursor: Option[String],
                              walletId: UUID
                             )

object CommandLineOptions {
  val opts: Opts[CommandLineOptions] = {
    val xpubOpts = Opts.option[String]("xpub", "The extended public key")
    val syncId = Opts.option[UUID]("syncId", "The synchronization id")
    val cursor = Opts.option[String]("cursor", "The current hash of the blockchain").orNone
    val walletId = Opts.option[UUID]("walletId", "The id of the wallet the xpub belongs to")

    (xpubOpts, syncId, cursor, walletId).mapN(CommandLineOptions(_, _, _, _))
  }

  val command: Command[CommandLineOptions] = Command("cria-worker-bitcoin", "The cria synchronization worker for BTC")(opts)
}