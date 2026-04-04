import sbt.*
import sbt.Keys.*

ThisBuild / organization := "io.alnovis.ircraft"
// version is managed by sbt-dynver from git tags (e.g., v2.0.0-alpha.1 -> 2.0.0-alpha.1)
ThisBuild / scalaVersion := "3.6.4"
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / homepage := Some(url("https://github.com/alnovis/ircraft"))
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
  Developer("alnovis", "alnovis", "alnovis@alnovis.io", url("https://alnovis.io"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/alnovis/ircraft"), "scm:git@github.com:alnovis/ircraft.git")
)

// Sonatype Central Portal publishing
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"
ThisBuild / sonatypeRepository := "https://central.sonatype.com/api/v1/publisher/"

val commonSettings = Seq(
  javacOptions ++= Seq("-source", "17", "-target", "17"),
  scalacOptions ++= Seq(
    "-encoding", "utf-8",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Wunused:all",
    "-Xfatal-warnings",
  ),
  testFrameworks += new TestFramework("munit.Framework"),
)

val catsVersion       = "2.12.0"
val catsEffectVersion = "3.5.7"
val munitVersion      = "1.1.0"
val munitCEVersion    = "2.0.0"

// -- Modules --------------------------------------------------------------

lazy val root = (project in file("."))
  .aggregate(core, emit, io, dialectProto, emitterJava, emitterScala, examples)
  .settings(
    name := "ircraft",
    publish / skip := true,
  )

lazy val core = (project in file("ircraft-core"))
  .settings(commonSettings)
  .settings(
    name := "ircraft-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.scalameta" %% "munit"     % munitVersion % Test,
    ),
  )

lazy val emit = (project in file("ircraft-emit"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "ircraft-emit",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val io = (project in file("ircraft-io"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "ircraft-io",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect"       % catsEffectVersion,
      "org.typelevel" %% "munit-cats-effect" % munitCEVersion % Test,
      "org.scalameta" %% "munit"             % munitVersion   % Test,
    ),
  )

lazy val dialectProto = (project in file("dialects/proto"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "ircraft-dialect-proto",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val emitterJava = (project in file("emitters/java"))
  .dependsOn(core, emit)
  .settings(commonSettings)
  .settings(
    name := "ircraft-emitter-java",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val emitterScala = (project in file("emitters/scala"))
  .dependsOn(core, emit)
  .settings(commonSettings)
  .settings(
    name := "ircraft-emitter-scala",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val examples = (project in file("examples"))
  .dependsOn(core, emit, emitterJava)
  .settings(commonSettings)
  .settings(
    name := "ircraft-examples",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion,
      "org.scalameta" %% "munit"       % munitVersion % Test,
    ),
  )
