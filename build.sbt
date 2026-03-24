import sbt.*
import sbt.Keys.*

ThisBuild / organization := "io.alnovis.ircraft"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.4"

ThisBuild / homepage := Some(url("https://github.com/alnovis/ircraft"))
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
  Developer("alnovis", "alnovis", "alnovis@alnovis.io", url("https://alnovis.io"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/alnovis/ircraft"), "scm:git@github.com:alnovis/ircraft.git")
)

// Common settings
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

val munitVersion = "1.1.0"

// ─── Modules ─────────────────────────────────────────────────────────────────

lazy val root = (project in file("."))
  .aggregate(core, dialectProto, dialectSemantic, dialectJava, dialectKotlin, dialectScala, pipelineProtoToJava)
  .settings(
    name := "ircraft",
    publish / skip := true,
  )

lazy val core = (project in file("ircraft-core"))
  .settings(commonSettings)
  .settings(
    name := "ircraft-core",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

// ─── Dialects ────────────────────────────────────────────────────────────────

lazy val dialectSemantic = (project in file("dialects/semantic"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "ircraft-dialect-semantic",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val dialectProto = (project in file("dialects/proto"))
  .dependsOn(core, dialectSemantic)
  .settings(commonSettings)
  .settings(
    name := "ircraft-dialect-proto",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val dialectJava = (project in file("dialects/java"))
  .dependsOn(core, dialectSemantic)
  .settings(commonSettings)
  .settings(
    name := "ircraft-dialect-java",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val dialectKotlin = (project in file("dialects/kotlin"))
  .dependsOn(core, dialectSemantic)
  .settings(commonSettings)
  .settings(
    name := "ircraft-dialect-kotlin",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

lazy val dialectScala = (project in file("dialects/scala"))
  .dependsOn(core, dialectSemantic)
  .settings(commonSettings)
  .settings(
    name := "ircraft-dialect-scala",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )

// ─── Pipelines ───────────────────────────────────────────────────────────────

lazy val pipelineProtoToJava = (project in file("pipelines/proto-to-java"))
  .dependsOn(core, dialectProto, dialectSemantic, dialectJava)
  .settings(commonSettings)
  .settings(
    name := "ircraft-pipeline-proto-to-java",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % munitVersion % Test,
    ),
  )
