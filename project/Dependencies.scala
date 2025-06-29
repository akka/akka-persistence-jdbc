import sbt._

object Dependencies {

  val Scala213 = "2.13.15"
  val Scala3 = "3.3.4"

  val ScalaVersions = Seq(Scala213, Scala3)

  val AkkaVersion = "2.10.5"
  val AkkaBinaryVersion = VersionNumber(AkkaVersion).numbers match { case Seq(major, minor, _*) => s"$major.$minor" }

  val SlickVersion = "3.5.1"
  val ScalaTestVersion = "3.2.19"

  val JdbcDrivers = Seq(
    "org.postgresql" % "postgresql" % "42.7.6",
    "com.h2database" % "h2" % "2.3.232",
    "com.mysql" % "mysql-connector-j" % "9.3.0",
    "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8")

  val Libraries: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    "com.typesafe.slick" %% "slick" % SlickVersion,
    "org.slf4j" % "slf4j-api" % "2.0.17",
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
    "ch.qos.logback" % "logback-classic" % "1.5.18" % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Test)

  val Migration: Seq[ModuleID] = Seq(
    "com.typesafe" % "config" % "1.4.3",
    "ch.qos.logback" % "logback-classic" % "1.5.18",
    "org.testcontainers" % "postgresql" % "1.21.1" % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Provided)
}
