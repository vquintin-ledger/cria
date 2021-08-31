import sbt.librarymanagement.{DependencyBuilders, LibraryManagementSyntax, ModuleID}

object Dependencies extends DependencyBuilders with LibraryManagementSyntax {

  val http4sVersion = "0.21.22"
  val http4s: Seq[ModuleID] = Seq(
    "org.http4s" %% "http4s-blaze-server"       % http4sVersion,
    "org.http4s" %% "http4s-blaze-client"       % http4sVersion,
    "org.http4s" %% "http4s-circe"              % http4sVersion,
    "org.http4s" %% "http4s-dsl"                % http4sVersion,
    "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion
  )

  val circeVersion = "0.13.0"
  val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core"           % circeVersion,
    "io.circe" %% "circe-parser"         % circeVersion,
    "io.circe" %% "circe-generic"        % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion,
    "io.circe" %% "circe-refined"        % circeVersion
  )

  val refinedVersion = "0.9.26"
  val refined: Seq[ModuleID] = Seq(
    "eu.timepit" %% "refined"      % refinedVersion,
    "eu.timepit" %% "refined-cats" % refinedVersion
  )

  val H2Version       = "1.4.200"
  val flywayVersion   = "7.8.1"
  val doobieVersion   = "0.13.1"
  val postgresVersion = "42.2.22"
  val postgres: Seq[ModuleID] = Seq(
    "com.h2database" % "h2"              % H2Version,
    "org.flywaydb"   % "flyway-core"     % flywayVersion,
    "org.tpolecat"  %% "doobie-core"     % doobieVersion,
    "org.tpolecat"  %% "doobie-postgres" % doobieVersion,
    "org.tpolecat"  %% "doobie-hikari"   % doobieVersion,
    "org.tpolecat"  %% "doobie-h2"       % doobieVersion,
    "org.tpolecat"  %% "doobie-refined"  % doobieVersion,
    "org.postgresql" % "postgresql"      % postgresVersion
  )

  val pureconfigVersion   = "0.15.0"
  val logbackVersion      = "1.2.3"
  val logbackJsonVersion  = "6.6"
  val fs2Version          = "2.5.5"
  val fs2GrpcVersion      = "0.9.0"
  val protobufJava        = "3.15.8"
  val scalaLoggingVersion = "3.9.3"
  val utilities: Seq[ModuleID] = Seq(
    "com.typesafe.scala-logging" %% "scala-logging"            % scalaLoggingVersion,
    "co.fs2"                     %% "fs2-core"                 % fs2Version,
    "org.lyranthe.fs2-grpc"      %% "java-runtime"             % fs2GrpcVersion,
    "ch.qos.logback"              % "logback-classic"          % logbackVersion,
    "net.logstash.logback"        % "logstash-logback-encoder" % logbackJsonVersion,
    "com.github.pureconfig"      %% "pureconfig"               % pureconfigVersion,
    "com.github.pureconfig"      %% "pureconfig-cats"          % pureconfigVersion
  )

  val declineVersion = "2.0.0"
  val cli: Seq[ModuleID] = Seq(
    "com.monovore" %% "decline" % declineVersion
  )

  val scalaTestVersion           = "3.2.8"
  val scalaTestPlusVersion       = "3.2.2.0"
  val scalaCheckVersion          = "1.15.3"
  val otjPgEmbeddedVersion       = "0.13.3"
  val embeddedRedisVersion       = "0.7.3"
  val testcontainersScalaVersion = "0.39.5"
  val disciplineVersion          = "1.1.5"
  val catsVersion                = "2.6.1"
  val disciplineScalaTestVersion = "2.1.5"
  val catsScalacheckVersion      = "0.3.1"
  val test: Seq[ModuleID] = Seq(
    "org.scalatest"           %% "scalatest"                      % scalaTestVersion           % "it, test, e2e",
    "io.chrisdavenport"       %% "cats-scalacheck"                % catsScalacheckVersion      % "pbt",
    "org.scalacheck"          %% "scalacheck"                     % scalaCheckVersion          % "it, test, e2e",
    "org.scalatestplus"       %% "scalacheck-1-14"                % scalaTestPlusVersion       % "it, test, e2e",
    "org.tpolecat"            %% "doobie-scalatest"               % doobieVersion              % "it, test, e2e",
    "org.typelevel"           %% "cats-laws"                      % catsVersion                % "pbt",
    "org.typelevel"           %% "discipline-core"                % disciplineVersion          % "pbt",
    "org.typelevel"           %% "discipline-scalatest"           % disciplineScalaTestVersion % "pbt",
    "com.opentable.components" % "otj-pg-embedded"                % otjPgEmbeddedVersion       % Test,
    "it.ozimov"                % "embedded-redis"                 % embeddedRedisVersion       % Test,
    "com.dimafeng"            %% "testcontainers-scala-scalatest" % testcontainersScalaVersion % "test, it, e2e"
  )

  // https://scalapb.github.io/docs/faq/#i-am-getting-import-was-not-found-or-had-errors
  val commonProtos: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime"   % scalapb.compiler.Version.scalapbVersion % "protobuf",
    "io.grpc"               % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
    "com.google.protobuf"   % "protobuf-java"     % protobufJava
  )

  val cria: Seq[ModuleID] = circe ++ utilities ++ postgres ++ http4s ++ cli ++ refined
}
