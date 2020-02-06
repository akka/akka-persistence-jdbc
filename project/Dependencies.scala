import sbt._
import Keys._

object Dependencies {
  val Nightly = sys.env.get("TRAVIS_EVENT_TYPE").contains("cron")

  // Keep in sync with .travis.yml
  val Scala212 = "2.12.10"
  val Scala213 = "2.13.1"
  val ScalaVersions = Seq(Scala212, Scala213)

  val AkkaVersion = if (Nightly) "2.6.0" else "2.5.25"
  val AkkaBinaryVersion = if (Nightly) "2.6" else "2.5"

  val SlickVersion = "3.3.2"
  val ScalaTestVersion = "3.0.8"

  val JdbcDrivers = Seq(
    "org.postgresql" % "postgresql" % "42.2.10",
    "com.h2database" % "h2" % "1.4.200",
    "mysql" % "mysql-connector-java" % "8.0.19",
    "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8")

  val Libraries: Seq[ModuleID] = Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Test)

  val Migration: Seq[ModuleID] = Seq(
      "org.flywaydb" % "flyway-core" % "6.2.1",
      "com.typesafe" % "config" % "1.4.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.testcontainers" % "postgresql" % "1.12.5" % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Provided)
}
