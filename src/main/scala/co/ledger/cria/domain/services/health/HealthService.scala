package co.ledger.cria.domain.services.health

import buildinfo.BuildInfo
import cats.effect.{ConcurrentEffect, IO}
import co.ledger.protobuf.HealthCheckResponse._
import co.ledger.protobuf._
import io.grpc.{Metadata, ServerServiceDefinition}

class HealthService extends HealthFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    HealthFs2Grpc.bindService(this)

  def check(request: HealthCheckRequest, ctx: Metadata): IO[HealthCheckResponse] =
    IO.pure(HealthCheckResponse(ServingStatus.SERVING))

  def watch(request: HealthCheckRequest, ctx: Metadata): fs2.Stream[IO, HealthCheckResponse] =
    fs2.Stream(HealthCheckResponse(ServingStatus.SERVING))

  def version(request: Empty, ctx: Metadata): IO[VersionResponse] =
    IO.pure(VersionResponse(BuildInfo.version, BuildInfo.gitHeadCommit.getOrElse("n/a")))
}
