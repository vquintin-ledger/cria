import sbt.Compile
// Build shared info
ThisBuild / organization := "co.ledger"
ThisBuild / scalaVersion := "2.13.3"
ThisBuild / resolvers += Resolver.sonatypeRepo("releases")
ThisBuild / scalacOptions ++= CompilerFlags.all

// Dynver settings
ThisBuild / dynverVTagPrefix := false
ThisBuild / dynverSeparator := "-"

// Shared Plugins
enablePlugins(BuildInfoPlugin)
ThisBuild / libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

lazy val disableDocGeneration = Seq(
  Compile / doc / sources := Seq.empty,
  Compile / packageDoc / publishArtifact := false
)

lazy val ignoreFiles = List("application.conf.sample")

// Runtime
scalaVersion := "2.13.3"
scalacOptions ++= CompilerFlags.all
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](version, git.gitHeadCommit),
  buildInfoPackage := "buildinfo"
)

lazy val dockerSettings = Seq(
  dockerBaseImage := "openjdk:14.0.2",
  dockerRepository := Some("docker.pkg.github.com/ledgerhq/cria"),
  dockerUpdateLatest := true, //should always update latest
  javaAgents += "com.datadoghq" % "dd-java-agent" % "0.78.3"
)

lazy val coverageSettings = Seq(
  coverageMinimum := 0,
  coverageFailOnMinimum := false
)

lazy val sharedSettings =
  dockerSettings ++ Defaults.itSettings ++ coverageSettings ++ disableDocGeneration

lazy val criaProtobuf = (project in file("protobuf"))
  .enablePlugins(Fs2Grpc)
  .settings(
    name := "cria-protobuf",
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    libraryDependencies ++= Dependencies.commonProtos
  )
  .settings(disableDocGeneration)

// Common cria library
lazy val common = (project in file("common"))
  .enablePlugins(BuildInfoPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "cria-common",
    libraryDependencies ++= (Dependencies.criaCommon ++ Dependencies.test)
  )
  .settings(disableDocGeneration, buildInfoSettings)
  .dependsOn(criaProtobuf)

lazy val accountManager = (project in file("account-manager"))
  .enablePlugins(JavaAgent, JavaServerAppPackaging, DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "cria-account-manager",
    sharedSettings,
    libraryDependencies ++= (Dependencies.accountManager ++ Dependencies.test)
  )
  .dependsOn(common)

lazy val bitcoinProtobuf = (project in file("coins/bitcoin/protobuf"))
  .enablePlugins(Fs2Grpc)
  .settings(
    name := "cria-bitcoin-protobuf",
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    libraryDependencies ++= Dependencies.commonProtos,
    PB.protoSources in Compile ++= Seq(
      file("coins/bitcoin/keychain/pb/keychain")
    )
  )
  .settings(disableDocGeneration)

lazy val bitcoinCommon = (project in file("coins/bitcoin/common"))
  .configs(IntegrationTest)
  .settings(
    name := "cria-bitcoin-common",
    libraryDependencies ++= Dependencies.btcCommon
  )
  .settings(disableDocGeneration)
  .dependsOn(common, bitcoinProtobuf)

lazy val bitcoinWorker = (project in file("coins/bitcoin/worker"))
  .enablePlugins(JavaAgent, JavaServerAppPackaging, DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "cria-bitcoin-worker",
    sharedSettings,
    libraryDependencies ++= (Dependencies.btcWorker ++ Dependencies.test)
  )
  .dependsOn(common, bitcoinCommon)

lazy val bitcoinInterpreter = (project in file("coins/bitcoin/interpreter"))
  .enablePlugins(JavaAgent, JavaServerAppPackaging, DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "cria-bitcoin-interpreter",
    sharedSettings,
    libraryDependencies ++= (Dependencies.btcInterpreter ++ Dependencies.test),
    parallelExecution in IntegrationTest := false
  )
  .dependsOn(common, bitcoinCommon)
