package co.ledger.cria.cli

import cats.data.{NonEmptyList, Validated}
import com.monovore.decline._
import cats.implicits._
import java.util.UUID

import co.ledger.cria.models.account.Coin

case class CommandLineOptions(
    keychainId: UUID,
    coin: Coin,
    syncId: UUID,
    blockHash: Option[String],
    walletUid: UUID
)

object CommandLineOptions {
  val opts: Opts[CommandLineOptions] = {
    val keychainId = Opts.option[UUID]("keychainId", "The keychain id")
    val syncId     = Opts.option[UUID]("syncId", "The synchronization id")
    val blockHash  = Opts.option[String]("blockHash", "The current hash of the blockchain").orNone
    val walletUid  = Opts.option[UUID]("walletUid", "The id of the wallet the xpub belongs to")
    val coin = Opts
      .option[String]("coin", "The coin to synchronize")
      .mapValidated(c =>
        Validated.fromOption(Coin.fromKey(c), NonEmptyList.one(s"$c is not a valid coin"))
      )

    (keychainId, coin, syncId, blockHash, walletUid).mapN(
      CommandLineOptions(_, _, _, _, _)
    )
  }

  val command: Command[CommandLineOptions] =
    Command("cria-worker-bitcoin", "The cria synchronization worker for BTC-like coins")(opts)
}
