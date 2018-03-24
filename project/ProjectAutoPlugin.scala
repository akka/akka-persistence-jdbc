import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import de.heikoseeberger.sbtheader.License.ALv2
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.headerLicense
import sbt.Keys._
import sbt._

import SbtScalariform.autoImport._
import scalariform.formatter.preferences.FormattingPreferences

object ProjectAutoPlugin extends AutoPlugin {
  final val AkkaVersion = "2.5.11"
  final val SlickVersion = "3.2.3"
  final val ScalaTestVersion = "3.0.3"

  final val formattingPreferences: FormattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
      .setPreference(DoubleIndentConstructorArguments, true)
  }

  override val requires = com.typesafe.sbt.SbtScalariform

  override val trigger: PluginTrigger = allRequirements

  object autoImport {
  }

  override val projectSettings: Seq[Setting[_]] = SbtScalariform.projectSettings ++ Seq(
    name := "akka-persistence-jdbc",
    organization := "com.github.dnvriend",
    organizationName := "Dennis Vriend",
    description := "A plugin for storing events in an event journal akka-persistence-jdbc",
    startYear := Some(2014),

    licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php")),

    scalaVersion := "2.12.5",

    crossScalaVersions := Seq("2.11.12", "2.12.5"),

    fork in Test := true,

    scalariformAutoformat := true,

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

    scalacOptions += "-Ypartial-unification",
    scalacOptions += "-Ydelambdafy:method",

    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),

    headerLicense := Some(ALv2("2018", "Dennis Vriend")),

    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.jcenterRepo,

    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences,

   libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
   libraryDependencies += "com.typesafe.slick" %% "slick" % SlickVersion,
   libraryDependencies += "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
   libraryDependencies += "org.postgresql" % "postgresql" % "42.2.2" % Test,
   libraryDependencies += "com.h2database" % "h2" % "1.4.197" % Test,
   libraryDependencies += "mysql" % "mysql-connector-java" % "6.0.6" % Test,
   libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
   libraryDependencies += "org.scalatest" %% "scalatest" % ScalaTestVersion % Test   
 )
}
