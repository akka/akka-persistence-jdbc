import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{ headerLicense, HeaderLicense }
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object ProjectAutoPlugin extends AutoPlugin {
  object autoImport {}

  override val requires = JvmPlugin && HeaderPlugin

  override def globalSettings =
    Seq(
      organization := "com.lightbend.akka",
      organizationName := "Lightbend Inc.",
      organizationHomepage := Some(url("https://www.lightbend.com/")),
      homepage := Some(url("https://github.com/akka/akka-persistence-jdbc")),
      scmInfo := Some(
          ScmInfo(
            url("https://github.com/akka/akka-persistence-jdbc"),
            "git@github.com:akka/akka-persistence-jdbc.git")),
      developers += Developer(
          "contributors",
          "Contributors",
          "https://gitter.im/akka/dev",
          url("https://github.com/akka/akka-persistence-jdbc/graphs/contributors")),
      licenses := Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
      description := "A plugin for storing events in an event journal akka-persistence-jdbc",
      startYear := Some(2014))

  override val trigger: PluginTrigger = allRequirements

  override val projectSettings: Seq[Setting[_]] = mimaDefaultSettings ++ Seq(
      crossVersion := CrossVersion.binary,
      crossScalaVersions := Dependencies.ScalaVersions,
      scalaVersion := Dependencies.Scala212,
      Test / fork := true,
      Test / parallelExecution := false,
      Test / logBuffered := true,
      scalacOptions ++= Seq(
          "-encoding",
          "UTF-8",
          "-deprecation",
          "-feature",
          "-unchecked",
          "-Xlog-reflective-calls",
          "-language:higherKinds",
          "-language:implicitConversions",
          "-target:jvm-1.8"),
      scalacOptions += {
        if (scalaVersion.value.startsWith("2.13")) ""
        else "-Ypartial-unification"
      },
      scalacOptions += "-Ydelambdafy:method",
      // show full stack traces and test case durations
      Test / testOptions += Tests.Argument("-oDF"),
      headerLicense := Some(
          HeaderLicense.Custom("""|Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
           |Copyright (C) 2019 - 2019 Lightbend Inc. <https://www.lightbend.com>
           |""".stripMargin)),
      resolvers += Resolver.typesafeRepo("releases"),
      resolvers += Resolver.jcenterRepo)

  def determineMimaPreviousArtifacts(scalaBinVersion: String): Set[ModuleID] = {
    val compatVersions: Set[String] =
      if (scalaBinVersion.startsWith("2.13")) Set("3.5.2")
      else {
        Set("3.5.0", "3.5.1", "3.5.2")
      }
    compatVersions.map { v =>
      "com.github.dnvriend" % ("akka-persistence-jdbc_" + scalaBinVersion) % v
    }
  }
}
