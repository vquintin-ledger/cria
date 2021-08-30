package co.ledger.cria.domain.models.interpreter

object SatoshisTestHelper {
  def unsafe(n: BigInt): Satoshis =
    Satoshis.asMonadError[Either[Throwable, *]](n).fold(throw _, identity)
}
