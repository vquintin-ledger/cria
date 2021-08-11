package co.ledger.cria.domain.services.interpreter

import cats.{Eq, Monad}
import cats.kernel.laws.discipline._
import co.ledger.cria.domain.models.Sort
import co.ledger.cria.domain.models.account.AccountUid
import co.ledger.cria.domain.models.interpreter.{BlockHeight, BlockView, Satoshis, TransactionView}
import org.scalacheck.Prop._
import org.scalacheck.Arbitrary
import org.typelevel.discipline.Laws

trait PersistenceFacadeTests[F[_]] extends Laws {

  def laws: PersistenceFacadeLaws[F]

  def persistenceFacade(implicit
                        eqFBool: Eq[F[Boolean]],
                        eqFSatoshis: Eq[F[Satoshis]],
                        eqFListTx: Eq[F[List[TransactionView]]],
                        arbTx: Arbitrary[TransactionView],
                        arbAccount: Arbitrary[AccountUid],
                        arbBlockHeight: Arbitrary[BlockHeight],
                        arbBlockView: Arbitrary[BlockView],
                        arbSort: Arbitrary[Sort]
  ): RuleSet =
    new DefaultRuleSet(
      name = "persistenceFacade",
      parent = None,
      "no more high height transactions after remove" -> forAll(
        laws.noMoreTransactionsAfterRemove _
      ),
      "can read saved transaction" -> forAll(
        laws.canReadSavedTransaction _
      ),
      "saving transactions updates block when it did not have one" -> forAll(
        laws.transactionIsUpdatedWhenItDoesNotHaveABlock _
      ),
      "saving transactions updates remove block (reorg) when it had one" -> forAll(
        laws.transactionIsUpdatedWhenItDoesNotHaveABlock _
      ),
      "transaction net output is sum of external outputs" -> forAll(
        laws.outputAmountIsSumOfExternalOutputs _
      ),
      "transaction change is sum of internal outputs" -> forAll(
        laws.changeAmountIsSumOfInternalOutputs _
      ),
      "transaction input is sum of inputs with a derivation" -> forAll(
        laws.inputAmountIsSumOfTxInputsWithDerivation _
      ),
      "operation fees match tx fees" -> forAll(
        laws.operationFeesMatchTxFees _
      ),
    )

}

object PersistenceFacadeTests {
  def apply[F[_]](persistenceFacade_ : => PersistenceFacade[F])(implicit
      F_ : Monad[F],
      fs2Compiler_ : fs2.Stream.Compiler[F, F]
  ): PersistenceFacadeTests[F] =
    new PersistenceFacadeTests[F] {
      override def laws: PersistenceFacadeLaws[F] = PersistenceFacadeLaws[F](persistenceFacade_)
    }
}
