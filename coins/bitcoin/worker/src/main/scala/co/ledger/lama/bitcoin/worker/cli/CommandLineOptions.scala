package co.ledger.lama.bitcoin.worker.cli

import cats.data.{NonEmptyList, Validated}
import com.monovore.decline._
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.Scheme
import co.ledger.lama.bitcoin.common.models.keychain.AccountKey.Xpub
import co.ledger.lama.common.models.Coin

import java.util.UUID

case class CommandLineOptions(
    xpub: Xpub,
    scheme: Scheme,
    coin: Coin,
    syncId: UUID,
    cursor: Option[String],
    walletId: UUID,
    lookahead: Int
)

object CommandLineOptions {
  val opts: Opts[CommandLineOptions] = {
    val xpub = Opts.option[String]("xpub", "The extended public key").map(Xpub(_))
    val scheme = Opts
      .option[String]("scheme", "The xpub scheme")
      .mapValidated(s =>
        Validated.fromOption(Scheme.fromKey(s), NonEmptyList.one(s"$s is not a valid scheme"))
      )
    val syncId   = Opts.option[UUID]("syncId", "The synchronization id")
    val cursor   = Opts.option[String]("cursor", "The current hash of the blockchain").orNone
    val walletId = Opts.option[UUID]("walletId", "The id of the wallet the xpub belongs to")
    val coin = Opts
      .option[String]("coin", "The coin to synchronize")
      .mapValidated(c =>
        Validated.fromOption(Coin.fromKey(c), NonEmptyList.one(s"$c is not a valid coin"))
      )
    val lookahead =
      Opts.option[Int]("lookahead", "The HD wallet (BIP-32) lookahead").withDefault(20)

    (xpub, scheme, coin, syncId, cursor, walletId, lookahead).mapN(
      CommandLineOptions(_, _, _, _, _, _, _)
    )
  }

  val command: Command[CommandLineOptions] =
    Command("cria-worker-bitcoin", "The cria synchronization worker for BTC-like coins")(opts)
}
