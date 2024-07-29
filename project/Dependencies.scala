import sbt._

object Dependencies {

  val Scala213 = "2.13.14"
  val Scala3 = "3.3.3"

  val ScalaVersions = Seq(Scala213, Scala3)

  val AkkaVersion = "2.9.3"
  val AkkaBinaryVersion = AkkaVersion.take(3)

  val SlickVersion = "3.5.1"
  val ScalaTestVersion = "3.2.19"

  val JdbcDrivers = Seq(
    "org.postgresql" % "postgresql" % "42.7.3",
    "com.h2database" % "h2" % "2.2.224",
    "com.mysql" % "mysql-connector-j" % "8.4.0",
    "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8")

  val Libraries: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    // Slick 3.5 pulls in slf4j-api 2.2 which doesn't work with Akka
    ("com.typesafe.slick" %% "slick" % SlickVersion).exclude("org.slf4j", "slf4j-api"),
    "org.slf4j" % "slf4j-api" % "1.7.36",
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.13" % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Test)

  val Migration: Seq[ModuleID] = Seq(
    "com.typesafe" % "config" % "1.4.3",
    "ch.qos.logback" % "logback-classic" % "1.2.13",
    "org.testcontainers" % "postgresql" % "1.20.0" % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Provided)
}
