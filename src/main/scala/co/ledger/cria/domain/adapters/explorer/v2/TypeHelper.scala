package co.ledger.cria.domain.adapters.explorer.v2

import cats.MonadError
import co.ledger.cria.clients.explorer.v2.models.{Block, DefaultInput}
import co.ledger.cria.clients.explorer.v2.{models => explorer}
import co.ledger.cria.domain.models.TxHash
import cats.implicits._
import co.ledger.cria.domain.models.interpreter._

object TypeHelper {
  object block {
    def toExplorer(b: BlockView): explorer.Block =
      Block(
        b.hash.asString,
        b.height.height.value,
        b.time
      )

    def fromExplorer[F[_]](b: explorer.Block)(implicit me: MonadError[F, Throwable]): F[BlockView] =
      for {
        hash <- me
          .fromEither(
            BlockHash
              .fromString(b.hash)
              .leftMap(s => new RuntimeException(s"Invalid block hash from explorer: $s"))
          )
        height <- me
          .fromEither(
            BlockHeight
              .fromLong(b.height)
              .leftMap(s => new RuntimeException(s"Invalid block height from explorer: $s"))
          )
      } yield BlockView(
        hash,
        height,
        b.time
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
        result <- transaction[F](t, txHash)
      } yield result
    }

    private def transaction[F[_]](t: explorer.Transaction, txHash: TxHash)(implicit
        me: MonadError[F, Throwable]
    ): F[TransactionView] = {
      for {
        inputs <- t.inputs.collect { case i: DefaultInput => i }.traverse(input.fromDefaultInput[F])
        block  <- t.block.traverse(block.fromExplorer[F])
      } yield {
        TransactionView(
          t.hash,
          txHash,
          t.receivedAt,
          t.lockTime,
          t.fees,
          inputs,
          t.outputs.map { o =>
            OutputView(
              o.outputIndex,
              o.value,
              o.address,
              o.scriptHex,
              None,
              None
            )
          },
          block,
          t.confirmations
        )
      }
    }
  }

  object input {
    def fromDefaultInput[F[_]](
        i: explorer.DefaultInput
    )(implicit me: MonadError[F, Throwable]): F[InputView] =
      for {
        outputHash <- me.fromEither(
          TxHash
            .fromString(i.outputHash)
            .leftMap(s => new RuntimeException(s"Not a valid txHash from explorer: $s"))
        )
      } yield {
        InputView(
          outputHash,
          i.outputIndex,
          i.inputIndex,
          i.value,
          i.address,
          i.scriptSignature,
          List.empty,
          i.sequence,
          None
        )
      }
  }
}
