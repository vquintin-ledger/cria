package co.ledger.cria.domain.adapters.explorer.v3

import cats.MonadError
import co.ledger.cria.clients.explorer.v3.models.{Block, DefaultInput}
import co.ledger.cria.clients.explorer.v3.{models => explorer}
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
      for {
        fees    <- Satoshis.asMonadError[F](t.fees)
        inputs  <- t.inputs.collect { case i: DefaultInput => i }.traverse(input.fromDefaultInput[F])
        outputs <- t.outputs.traverse(output.fromExplorer[F])
        result <- TransactionView.asMonadError[F](
          id = t.hash,
          hash = txHash,
          receivedAt = t.receivedAt,
          lockTime = t.lockTime,
          fees = fees,
          inputs = inputs,
          outputs = outputs,
          block = None,
          confirmations = t.confirmations
        )
      } yield result
    }

    private def confirmedTransaction[F[_]](t: explorer.ConfirmedTransaction, txHash: TxHash)(
        implicit me: MonadError[F, Throwable]
    ): F[TransactionView] = {
      for {
        blockView <- block.fromExplorer[F](t.block)
        fees      <- Satoshis.asMonadError[F](t.fees)
        inputs    <- t.inputs.collect { case i: DefaultInput => i }.traverse(input.fromDefaultInput[F])
        outputs   <- t.outputs.traverse(output.fromExplorer[F])
        result <- TransactionView.asMonadError[F](
          id = t.hash,
          hash = txHash,
          receivedAt = t.receivedAt,
          lockTime = t.lockTime,
          fees = fees,
          inputs = inputs,
          outputs = outputs,
          block = Some(blockView),
          confirmations = t.confirmations
        )
      } yield result
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
        value <- Satoshis.asMonadError[F](i.value)
      } yield {
        InputView(
          outputHash,
          i.outputIndex,
          i.inputIndex,
          value,
          i.address,
          i.scriptSignature,
          i.txinwitness,
          i.sequence,
          None
        )
      }
  }

  object output {
    def fromExplorer[F[_]](
        o: explorer.Output
    )(implicit me: MonadError[F, Throwable]): F[OutputView] =
      Satoshis
        .asMonadError[F](o.value)
        .map(value =>
          OutputView(
            o.outputIndex,
            value,
            o.address,
            o.scriptHex,
            None,
            None
          )
        )
  }
}
