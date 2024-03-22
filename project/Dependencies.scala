import sbt._

object Dependencies {

  val Scala213 = "2.13.13"
  val Scala3 = "3.3.3"

  val ScalaVersions = Seq(Scala213, Scala3)

  val AkkaVersion = "2.9.0"
  val AkkaBinaryVersion = AkkaVersion.take(3)

  val SlickVersion = "3.5.0"
  val ScalaTestVersion = "3.2.18"

  val JdbcDrivers = Seq(
    "org.postgresql" % "postgresql" % "42.7.3",
    "com.h2database" % "h2" % "2.2.224",
    "com.mysql" % "mysql-connector-j" % "8.3.0",
    "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8")

  val Libraries: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    "com.typesafe.slick" %% "slick" % SlickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
    "ch.qos.logback" % "logback-classic" % "1.5.3" % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Test)

  val Migration: Seq[ModuleID] = Seq(
    "com.typesafe" % "config" % "1.4.3",
    "ch.qos.logback" % "logback-classic" % "1.5.3",
    "org.testcontainers" % "postgresql" % "1.19.7" % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test) ++ JdbcDrivers.map(_ % Provided)
}
