package co.ledger.cria.domain.adapters.explorer

import cats.MonadError
import co.ledger.cria.clients.explorer.models.{Block, DefaultInput}
import co.ledger.cria.clients.explorer.{models => explorer}
import co.ledger.cria.domain.models.TxHash
import cats.implicits._
import co.ledger.cria.domain.models.interpreter.{
  BlockHash,
  BlockView,
  Coin,
  InputView,
  OutputView,
  TransactionView
}

object TypeHelper {
  object block {
    def toExplorer(b: BlockView): explorer.Block =
      Block(
        b.hash.asString,
        b.height,
        b.time
      )

    def fromExplorer[F[_]](b: explorer.Block)(implicit me: MonadError[F, Throwable]): F[BlockView] =
      me
        .fromEither(
          BlockHash
            .fromString(b.hash)
            .leftMap(s => new RuntimeException(s"Invalid block hash from explorer: $s"))
        )
        .map(hash =>
          BlockView(
            hash,
            b.height,
            b.time
          )
        )
  }

  object coin {
    def toExplorer(c: Coin): explorer.Coin =
      c match {
        case Coin.Btc        => explorer.Coin.Btc
        case Coin.Ltc        => explorer.Coin.Ltc
        case Coin.BtcTestnet => explorer.Coin.BtcTestnet
        case Coin.BtcRegtest => explorer.Coin.BtcRegtest
      }
  }

  object transaction {
    def fromExplorer[F[_]](
        t: explorer.Transaction
    )(implicit me: MonadError[F, Throwable]): F[TransactionView] = {
      for {
        txHash <- me.fromEither(
          TxHash
            .fromString(t.hash)
            .leftMap(s => new RuntimeException(s"Invalid hash from explorer: $s"))
        )
        result <-
          t match {
            case t: explorer.ConfirmedTransaction   => confirmedTransaction[F](t, txHash)
            case t: explorer.UnconfirmedTransaction => unconfirmedTransaction[F](t, txHash)
          }
      } yield result
    }

    private def unconfirmedTransaction[F[_]](t: explorer.UnconfirmedTransaction, txHash: TxHash)(
        implicit me: MonadError[F, Throwable]
    ): F[TransactionView] = {
      import t._
      me.pure(
        TransactionView(
          id,
          txHash,
          receivedAt,
          lockTime,
          fees,
          inputs.collect { case i: DefaultInput =>
            InputView(
              i.outputHash,
              i.outputIndex,
              i.inputIndex,
              i.value,
              i.address,
              i.scriptSignature,
              i.txinwitness,
              i.sequence,
              None
            )
          },
          outputs.map { o =>
            OutputView(
              o.outputIndex,
              o.value,
              o.address,
              o.scriptHex,
              None,
              None
            )
          },
          None,
          confirmations
        )
      )
    }

    private def confirmedTransaction[F[_]](t: explorer.ConfirmedTransaction, txHash: TxHash)(
        implicit me: MonadError[F, Throwable]
    ): F[TransactionView] = {
      import t.{block => b, _}
      block
        .fromExplorer[F](b)
        .map(blockView =>
          TransactionView(
            id,
            txHash,
            receivedAt,
            lockTime,
            fees,
            inputs.collect { case i: DefaultInput =>
              InputView(
                i.outputHash,
                i.outputIndex,
                i.inputIndex,
                i.value,
                i.address,
                i.scriptSignature,
                i.txinwitness,
                i.sequence,
                None
              )
            },
            outputs.map { o =>
              OutputView(
                o.outputIndex,
                o.value,
                o.address,
                o.scriptHex,
                None,
                None
              )
            },
            Some(blockView),
            confirmations
          )
        )
    }
  }
}
