import sbt._
import Keys._

object Dependencies {
  val Nightly = sys.env.get("TRAVIS_EVENT_TYPE").contains("cron")

  // Keep in sync with .travis.yml
  val Scala212 = "2.12.11"
  val Scala213 = "2.13.1"
  val ScalaVersions = Seq(Scala212, Scala213)

  val AkkaVersion = "2.6.5"
  val AkkaBinaryVersion = "2.6"

  val SlickVersion = "3.3.2"
  val ScalaTestVersion = "3.1.3"

  val JdbcDrivers = Seq(
    "org.postgresql" % "postgresql" % "42.2.16",
    "com.h2database" % "h2" % "1.4.200",
    "mysql" % "mysql-connector-java" % "8.0.21",
    "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8")

  val Libraries: Seq[ModuleID] = Seq(
      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Test)

  val Migration: Seq[ModuleID] = Seq(
      "org.flywaydb" % "flyway-core" % "6.4.4",
      "com.typesafe" % "config" % "1.4.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.testcontainers" % "postgresql" % "1.14.3" % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Provided)
}
