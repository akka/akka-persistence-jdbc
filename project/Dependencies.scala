import sbt._

object Dependencies {

  val Scala213 = "2.13.15"
  val Scala3 = "3.3.4"

  val ScalaVersions = Seq(Scala213, Scala3)

  val AkkaVersion = "2.10.0"
  val AkkaBinaryVersion = VersionNumber(AkkaVersion).numbers match { case Seq(major, minor, _*) => s"$major.$minor" }

  val SlickVersion = "3.5.2"
  val ScalaTestVersion = "3.2.19"

  val JdbcDrivers = Seq(
    "org.postgresql" % "postgresql" % "42.7.4",
    "com.h2database" % "h2" % "2.3.232",
    "com.mysql" % "mysql-connector-j" % "9.0.0",
    "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8")

  val Libraries: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    "com.typesafe.slick" %% "slick" % SlickVersion,
    "org.slf4j" % "slf4j-api" % "2.0.16",
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
    "ch.qos.logback" % "logback-classic" % "1.5.8" % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Test)

  val Migration: Seq[ModuleID] = Seq(
    "com.typesafe" % "config" % "1.4.3",
    "ch.qos.logback" % "logback-classic" % "1.5.8",
    "org.testcontainers" % "postgresql" % "1.20.3" % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Provided)
}
