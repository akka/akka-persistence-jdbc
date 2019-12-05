import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.core.IncompatibleResultTypeProblem
import com.typesafe.tools.mima.core.ProblemFilters
import com.typesafe.tools.mima.plugin.MimaKeys.{mimaBinaryIssueFilters, mimaPreviousArtifacts}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{HeaderLicense, headerLicense}
import sbt.Keys._
import sbt._
import SbtScalariform.autoImport._
import scalariform.formatter.preferences.FormattingPreferences

object ProjectAutoPlugin extends AutoPlugin {
  final val ScalaVersion = "2.13.0"
  final val AkkaVersion = "2.5.25"
  final val SlickVersion = "3.3.2"
  final val ScalaTestVersion = "3.0.8"

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

  override val projectSettings: Seq[Setting[_]] = SbtScalariform.projectSettings ++ mimaDefaultSettings ++ Seq(
    name := "akka-persistence-jdbc",
    organization := "com.lightbend.akka",
    organizationName := "Lightbend Inc.",
    organizationHomepage := Some(url("https://www.lightbend.com/")),
    homepage := Some(url("https://github.com/akka/akka-persistence-jdbc")),
    scmInfo := Some(ScmInfo(url("https://github.com/akka/akka-persistence-jdbc"), "git@github.com:akka/akka-persistence-jdbc.git")),
    developers += Developer("contributors",
      "Contributors",
      "https://gitter.im/akka/dev",
      url("https://github.com/akka/akka-persistence-jdbc/graphs/contributors")),
    licenses := Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
    description := "A plugin for storing events in an event journal akka-persistence-jdbc",
    startYear := Some(2014),

    scalaVersion := ScalaVersion,

    crossScalaVersions := Seq("2.11.12", "2.12.8", ScalaVersion),

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

    scalacOptions += {
      if (scalaVersion.value.startsWith("2.13")) ""
      else "-Ypartial-unification"
    },
    scalacOptions += "-Ydelambdafy:method",

    // show full stack traces and test case durations
    testOptions in Test += Tests.Argument("-oDF"),

    headerLicense := Some(
      HeaderLicense.Custom(
        """|Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
           |Copyright (C) 2019 - 2019 Lightbend Inc. <https://www.lightbend.com>
           |""".stripMargin
      )
    ),

    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.jcenterRepo,

    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences,
    mimaPreviousArtifacts := determineMimaPreviousArtifacts(scalaBinaryVersion.value),
    mimaBinaryIssueFilters ++= Seq(
      // Scala 2.11 issue which occurs because the signature of an internal lambda has changed. This lambda is not accessible outside of the method itself
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("akka.persistence.jdbc.util.DefaultSlickDatabaseProvider#lambda#1.apply")
    ),
   libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
   libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
   libraryDependencies += "com.typesafe.slick" %% "slick" % SlickVersion,
   libraryDependencies += "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
   libraryDependencies += "org.postgresql" % "postgresql" % "42.2.5" % Test,
   libraryDependencies += "com.h2database" % "h2" % "1.4.199" % Test,
   libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.15" % Test,
   libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "7.2.1.jre8" % Test,
   libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
   libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
   libraryDependencies += "org.scalatest" %% "scalatest" % ScalaTestVersion % Test   
 )

  def determineMimaPreviousArtifacts(scalaBinVersion: String): Set[ModuleID] = {
    val compatVersions: Set[String] = if (scalaBinVersion.startsWith("2.13")) Set("3.5.2") else {
      Set("3.5.0", "3.5.1", "3.5.2")
    }
    compatVersions.map { v =>
      "com.github.dnvriend" % ("akka-persistence-jdbc_" + scalaBinVersion) % v
    }
  }
}
