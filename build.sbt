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

// e2e tests
lazy val End2EndTest = config("e2e") extend Test
scalaSource in End2EndTest := baseDirectory.value / "src/it/scala"
unmanagedResourceDirectories in End2EndTest += baseDirectory.value / "src" / "it" / "e2e-resources"
parallelExecution in End2EndTest := false

// Assign the proper tests to each test conf
def e2eFilter(name: String): Boolean = name endsWith "E2ETest"
def itFilter(name: String): Boolean  = (name endsWith "IT") && !e2eFilter(name)
testOptions in IntegrationTest := Seq(Tests.Filter(itFilter))
testOptions in End2EndTest := Seq(Tests.Filter(e2eFilter))

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](version, git.gitHeadCommit),
  buildInfoPackage := "buildinfo"
)

lazy val dockerSettings = Seq(
  dockerBaseImage := "openjdk:14.0.2",
  dockerRepository := Some("ghcr.io"),
  dockerUsername := Some("ledgerhq"),
  dockerUpdateLatest := true, //should always update latest
  javaAgents += "com.datadoghq" % "dd-java-agent" % "0.78.3"
)

lazy val coverageSettings = Seq(
  coverageMinimum := 0,
  coverageFailOnMinimum := false
)

lazy val sharedSettings =
  dockerSettings ++ Defaults.itSettings ++ coverageSettings ++ disableDocGeneration

lazy val bitcoinProtobuf = (project in file("protobuf"))
  .enablePlugins(Fs2Grpc)
  .settings(
    name := "cria-bitcoin-protobuf",
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    libraryDependencies ++= Dependencies.commonProtos,
    PB.protoSources in Compile ++= Seq(
      file("keychain/pb/keychain")
    )
  )
  .settings(disableDocGeneration)

lazy val cria = (project in file("."))
  .enablePlugins(JavaAgent, JavaServerAppPackaging, DockerPlugin)
  .configs(IntegrationTest)
  .configs(End2EndTest)
  .settings(inConfig(End2EndTest)(Defaults.testSettings))
  .settings(buildInfoSettings)
  .settings(
    name := "cria",
    sharedSettings,
    libraryDependencies ++= (Dependencies.cria ++ Dependencies.test)
  )
  .dependsOn(bitcoinProtobuf)
