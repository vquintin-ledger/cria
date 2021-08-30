package co.ledger.cria.domain.adapters.keychain

import cats.effect.IO
import co.ledger.cria.domain.models.interpreter.Derivation
import co.ledger.cria.domain.models.keychain
import co.ledger.cria.domain.models.keychain.ChangeType.External
import co.ledger.cria.domain.models.keychain.{AccountAddress, ChangeType, KeychainId}
import co.ledger.cria.domain.services.keychain.KeychainClient

import scala.collection.mutable

class KeychainClientMock extends KeychainClient {

  var usedAddresses: mutable.Seq[String] = mutable.Seq.empty

  private val derivations: Derivation = Derivation(1, 0)
  private val change: External.type   = ChangeType.External

  val derivedAddresses: List[AccountAddress] = List(
    AccountAddress("1MZbRqZGpiSWGRLg8DUdVrDKHwNe1oesUZ", change, derivations),
    keychain.AccountAddress("1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ", change, derivations),
    keychain.AccountAddress("1MfeDvj5AUBG4xVMrx1xPgmYdXQrzHtW5b", change, derivations),
    keychain.AccountAddress("1GgX4cGLiqF9p4Sd1XcPQhEAAhNDA4wLYS", change, derivations),
    keychain.AccountAddress("1Q2Bv9X4yCTNn1P1tmFuWpijHvT3xYt3F", change, derivations),
    keychain.AccountAddress("1G7g5zxfjWCSJRuNKVasVczrZNowQRwbij", change, derivations),
    keychain.AccountAddress("1MFjwXsibXbvVzkE4chJrhbczDivpbbVTE", change, derivations),
    keychain.AccountAddress("1HFzpigeFDZGp45peU4NAHLgyMxiGj1GzT", change, derivations),
    keychain.AccountAddress("17xsjFyLgbWrjauC8F5hyaaaWdf6L6Y6L4", change, derivations),
    keychain.AccountAddress("1Hc7EofusKsUrNPhbp1PUMkH6wfDohfDBd", change, derivations),
    keychain.AccountAddress("1Mj9jzHtAyVvM9Y274LCcfLBBBfwRiDK9V", change, derivations),
    keychain.AccountAddress("1Ng5FPQ1rUbEHak8Qcjy6BRJhjF1n3AVR6", change, derivations),
    keychain.AccountAddress("145Tdk8ntZQa5kgyLheL835z6yukHjbEKF", change, derivations),
    keychain.AccountAddress("16hG8pC6D4gRmRvfHT3zHGcED9FMocN4hG", change, derivations),
    keychain.AccountAddress("1NQd72r3kUESTAMvDjaJU1Gk842HPcPVQQ", change, derivations),
    keychain.AccountAddress("1JiBkCdhc3P4by29kLzraz4CuwjAvTA96H", change, derivations),
    keychain.AccountAddress("1MXLmPcLRoQAWZqfgxtvhvUWLDQ3We2sUJ", change, derivations),
    keychain.AccountAddress("1DRCwCw8HjeRsRi4wyfJzqgBeNBJTdvvx1", change, derivations),
    keychain.AccountAddress("1NTG6NWQq1DZYZf8VQ58FBGGDwA9deM7Aq", change, derivations),
    keychain.AccountAddress("1JMbu32pdVu6FvKbmrJMTSJSWFcJJ47JtY", change, derivations),
    keychain.AccountAddress("13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ", change, derivations),
    keychain.AccountAddress("19rpjEgDaPUwkeyuD7JHKUkTyxFHAmnorm", change, derivations),
    keychain.AccountAddress("1D2R9GQu541rmUKY5kz6gjWuX2kfEusRre", change, derivations),
    keychain.AccountAddress("1B3g4WxFBJtPh6azgQdRs5f7zwXhcocELc", change, derivations),
    keychain.AccountAddress("12AdRB44ctyTaQiLgthz7WMFJ7dFNornmA", change, derivations),
    keychain.AccountAddress("1KHyosZPVXxVBaQ7qtRjPUWWt911rAkfg6", change, derivations),
    keychain.AccountAddress("1KConohwqXnB87BYpp2n7GfrPRhPqa471a", change, derivations),
    keychain.AccountAddress("1BGCPcrzx3G48eY7vhpc7UEtJbpXW3mZ1t", change, derivations),
    keychain.AccountAddress("14er8aopUkpX4KcL9rx7GU2t8zbFANQyC3", change, derivations),
    keychain.AccountAddress("1LPR9mGFJrWkiMPj2HWfnBA5weEeKV2arY", change, derivations),
    keychain.AccountAddress("15M1GcHsakzQtxkVDcw92siMk3c3Ap3C5h", change, derivations),
    keychain.AccountAddress("1GWfouhfoTHctEeUCMd1tcF2cdkfuaSXdh", change, derivations),
    keychain.AccountAddress("1CyAcL6Kd5pWzFucQE2Ev527FEQ9dTtPJ1", change, derivations),
    keychain.AccountAddress("1AxhDoozM9VfsktCKVN7kp6UkaqVq65rHF", change, derivations),
    keychain.AccountAddress("1Aj3Gi1j5UsvZh4ccjaqdnogPMWy54Z5ii", change, derivations)
  ) ++ (1 to 20).map(i => keychain.AccountAddress(s"unused$i", change, derivations))

  private val derivationsInternal: Derivation = Derivation(1, 1)
  val derivedAddressesInternal: List[AccountAddress] =
    (1 to 20)
      .map(i => keychain.AccountAddress(s"changeAddr$i", ChangeType.Internal, derivationsInternal))
      .toList

  override def getLookaheadSize(keychainId: KeychainId): IO[Int] = IO.pure(20)

  def markAddressesAsUsed(keychainId: KeychainId, addresses: List[String]): IO[Unit] =
    IO.delay {
      usedAddresses = usedAddresses ++ addresses
    }

  def getAddresses(
      keychainId: KeychainId,
      fromIndex: Int,
      toIndex: Int,
      changeType: Option[ChangeType]
  ): IO[List[AccountAddress]] =
    if (changeType.getOrElse(ChangeType.External) == ChangeType.External) {
      IO.delay(derivedAddresses.slice(fromIndex, toIndex))
    } else {
      IO.delay(derivedAddressesInternal.slice(fromIndex, toIndex))
    }

  def getKnownAndNewAddresses(
      keychainId: KeychainId,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]] =
    for {
      knownAddresses <- IO(
        derivedAddresses.filter(a => usedAddresses.contains(a.accountAddress))
      )

      newAddresses <- getAddresses(
        keychainId,
        knownAddresses.size - 1,
        knownAddresses.size + 20 - 1
      )

    } yield knownAddresses ++ newAddresses
}
