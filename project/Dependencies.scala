import sbt._
import Keys._

object Dependencies {
  val Nightly = sys.env.get("TRAVIS_EVENT_TYPE").contains("cron")

  val Scala212 = "2.12.8"
  val Scala213 = "2.13.0"
  val ScalaVersions = Seq(Scala212, Scala213)

  val AkkaVersion = if (Nightly) "2.6.0" else "2.5.25"
  val AkkaBinaryVersion = if (Nightly) "2.6" else "2.5"

  val SlickVersion = "3.3.2"
  val ScalaTestVersion = "3.0.8"

  val Libraries = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.slick" %% "slick" % SlickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
    "org.postgresql" % "postgresql" % "42.2.5" % Test,
    "com.h2database" % "h2" % "1.4.199" % Test,
    "mysql" % "mysql-connector-java" % "8.0.15" % Test,
    "com.microsoft.sqlserver" % "mssql-jdbc" % "7.2.1.jre8" % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test)
}
