package co.ledger.cria.cli

import cats.data.{NonEmptyList, Validated}
import com.monovore.decline._
import cats.implicits._
import java.util.UUID

import co.ledger.cria.domain.models.interpreter.{BlockHash, Coin, SyncId}
import co.ledger.cria.domain.models.account.{AccountUid, WalletUid}
import co.ledger.cria.domain.models.keychain.KeychainId

case class CommandLineOptions(
    keychainId: KeychainId,
    coin: Coin,
    syncId: SyncId,
    blockHash: Option[BlockHash],
    accountUid: AccountUid,
    walletUid: WalletUid
)

object CommandLineOptions {
  val opts: Opts[CommandLineOptions] = {
    val keychainId = Opts.option[UUID]("keychainId", "The keychain id").map(KeychainId.apply)
    val syncId     = Opts.option[UUID]("syncId", "The synchronization id").map(SyncId.apply)
    val blockHash =
      Opts
        .option[String]("blockHash", "The current hash of the blockchain")
        .mapValidated(h => Validated.fromEither(BlockHash.fromString(h).leftMap(NonEmptyList.one)))
        .orNone
    val walletUid = Opts
      .option[String]("walletUid", "The id of the wallet the xpub belongs to")
      .map(WalletUid.apply)
    val accountUid = Opts
      .option[String]("accountUid", "The id of the account the xpub belongs to")
      .map(AccountUid.apply)
    val coin = Opts
      .option[String]("coin", "The coin to synchronize")
      .mapValidated(c =>
        Validated.fromOption(Coin.fromKey(c), NonEmptyList.one(s"$c is not a valid coin"))
      )

    (keychainId, coin, syncId, blockHash, accountUid, walletUid).mapN(
      CommandLineOptions(_, _, _, _, _, _)
    )
  }

  val command: Command[CommandLineOptions] =
    Command("cria-worker-bitcoin", "The cria synchronization worker for BTC-like coins")(opts)
}
