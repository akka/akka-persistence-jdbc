import sbt._
import sbt.Keys._

import de.heikoseeberger.sbtheader._
import de.heikoseeberger.sbtheader.HeaderKey._
import de.heikoseeberger.sbtheader.license.Apache2_0
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

object ProjectAutoPlugin extends AutoPlugin {
  val AkkaVersion = "2.4.14"
  val SlickVersion = "3.1.1"
  val HikariCPVersion = "2.5.1"
  val ScalaTestVersion = "3.0.1"

  override def requires = com.typesafe.sbt.SbtScalariform

  override def trigger = allRequirements

  object autoImport {
  }

  import autoImport._

  override lazy val projectSettings = SbtScalariform.scalariformSettings ++ Seq(
    name := "akka-persistence-jdbc",
    organization := "com.github.dnvriend",
    organizationName := "Dennis Vriend",
    description := "A plugin for storing events in an event journal akka-persistence-jdbc",
    startYear := Some(2016),

    licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php")),

    scalaVersion := "2.11.8",

    fork in Test := true,

    logBuffered in Test := false,

    parallelExecution in Test := false,

    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-target:jvm-1.8"
    ),

    javacOptions ++= Seq(
      "-Xlint:unchecked"
    ),

    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),

    headers := headers.value ++ Map(
      "scala" -> Apache2_0("2016", "Dennis Vriend"),
      "conf" -> Apache2_0("2016", "Dennis Vriend", "#")
    ),

    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.jcenterRepo,
    resolvers += Resolver.bintrayRepo("scalaz", "releases"),
    resolvers += Resolver.bintrayRepo("stew", "snapshots"),

    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences,

   libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence-query-experimental" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
   libraryDependencies += "com.typesafe.slick" %% "slick" % SlickVersion,
   libraryDependencies += "com.typesafe.slick" %% "slick-extensions" % "3.1.0" % Test,
   libraryDependencies += "org.suecarter" %% "freeslick" % "3.1.1.1" % Test,
   libraryDependencies += "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion exclude("com.zaxxer", "HikariCP-java6"),
   libraryDependencies += "com.zaxxer" % "HikariCP" % HikariCPVersion,
   libraryDependencies += "org.postgresql" % "postgresql" % "9.4.1212" % Test,
   libraryDependencies += "com.h2database" % "h2" % "1.4.193" % Test,
   libraryDependencies += "mysql" % "mysql-connector-java" % "6.0.5" % Test,
   libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.7" % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
   libraryDependencies += "org.scalatest" %% "scalatest" % ScalaTestVersion % Test   
 )   

  def formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
      .setPreference(DoubleIndentClassDeclaration, true)
  }
}