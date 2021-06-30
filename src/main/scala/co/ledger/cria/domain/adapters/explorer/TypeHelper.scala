package co.ledger.cria.domain.adapters.explorer

import co.ledger.cria.clients.explorer.types.{Block, DefaultInput}
import co.ledger.cria.clients.explorer.{types => explorer}
import co.ledger.cria.domain.models.account.Coin
import co.ledger.cria.domain.models.interpreter.{
  BlockView,
  ConfirmedTransactionView,
  InputView,
  OutputView,
  TransactionView,
  UnconfirmedTransactionView
}

object TypeHelper {
  object block {
    def toExplorer(b: BlockView): explorer.Block =
      Block(
        b.hash,
        b.height,
        b.time
      )

    def fromExplorer(b: explorer.Block): BlockView = BlockView(b.hash, b.height, b.time)
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

  object confirmedTransaction {
    def fromExplorer(t: explorer.ConfirmedTransaction): ConfirmedTransactionView = {
      import t.{block => b, _}
      ConfirmedTransactionView(
        id,
        hash,
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
        BlockView(
          b.hash,
          b.height,
          b.time
        ),
        confirmations
      )
    }
  }

  object unconfirmedTransaction {
    def fromExplorer(t: explorer.UnconfirmedTransaction): UnconfirmedTransactionView = {
      import t._
      UnconfirmedTransactionView(
        id,
        hash,
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
        confirmations
      )
    }
  }

  object transaction {
    def fromExplorer(t: explorer.Transaction): TransactionView =
      t match {
        case t: explorer.ConfirmedTransaction   => confirmedTransaction.fromExplorer(t)
        case t: explorer.UnconfirmedTransaction => unconfirmedTransaction.fromExplorer(t)
      }
  }
}
