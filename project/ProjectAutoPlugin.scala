import com.geirsson.CiReleasePlugin
import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{ headerLicense, HeaderLicense }
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots

object ProjectAutoPlugin extends AutoPlugin {
  object autoImport {}

  override val requires = JvmPlugin && HeaderPlugin

  override def globalSettings =
    Seq(
      organization := "com.lightbend.akka",
      organizationName := "Lightbend Inc.",
      organizationHomepage := Some(url("https://www.lightbend.com/")),
      homepage := Some(url("https://doc.akka.io/docs/akka-persistence-jdbc/current/")),
      scmInfo := Some(
        ScmInfo(url("https://github.com/akka/akka-persistence-jdbc"), "git@github.com:akka/akka-persistence-jdbc.git")),
      developers += Developer(
        "contributors",
        "Contributors",
        "https://gitter.im/akka/dev",
        url("https://github.com/akka/akka-persistence-jdbc/graphs/contributors")),
      releaseNotesURL := (
        if ((ThisBuild / isSnapshot).value) None
        else Some(url(s"https://github.com/akka/akka-persistence-jdbc/releases/tag/v${version.value}"))
      ),
      licenses := {
        val tagOrBranch =
          if (version.value.endsWith("SNAPSHOT")) "master"
          else "v" + version.value
        Seq(("BUSL-1.1", url(s"https://raw.githubusercontent.com/akka/akka-persistence-jdbc/${tagOrBranch}/LICENSE")))
      },
      description := "A plugin for storing events in an event journal akka-persistence-jdbc",
      startYear := Some(2014))

  override val trigger: PluginTrigger = allRequirements

  override val projectSettings: Seq[Setting[_]] = Seq(
    crossVersion := CrossVersion.binary,
    crossScalaVersions := Dependencies.ScalaVersions,
    scalaVersion := Dependencies.Scala213,
    // append -SNAPSHOT to version when isSnapshot
    ThisBuild / dynverSonatypeSnapshots := true,
    Test / fork := false,
    Test / parallelExecution := false,
    Test / logBuffered := true,
    javacOptions ++= Seq("--release", "11"),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-unchecked",
      "-Xlog-reflective-calls",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-release",
      "11"),
    Compile / scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) =>
        disciplineScalacOptions -- Set(
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ypartial-unification",
          "-Yno-adapted-args")
      case Some((2, 12)) =>
        disciplineScalacOptions
      case _ =>
        Nil
    }).toSeq,
    scalacOptions += "-Ydelambdafy:method",
    Compile / doc / scalacOptions := scalacOptions.value ++ Seq(
      "-doc-title",
      "Akka Persistence JDBC",
      "-doc-version",
      version.value,
      "-sourcepath",
      (ThisBuild / baseDirectory).value.toString,
      "-skip-packages",
      "akka.pattern", // for some reason Scaladoc creates this
      "-doc-source-url", {
        val branch = if (isSnapshot.value) "master" else s"v${version.value}"
        s"https://github.com/akka/akka-persistence-jdbc/tree/${branch}€{FILE_PATH_EXT}#L€{FILE_LINE}"
      },
      "-doc-canonical-base-url",
      "https://doc.akka.io/api/akka-persistence-jdbc/current/"),
    // show full stack traces and test case durations
    Test / testOptions += Tests.Argument("-oDF"),
    headerLicense := Some(HeaderLicense.Custom("""|Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
           |Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
           |""".stripMargin)),
    resolvers += Resolver.jcenterRepo)

  val disciplineScalacOptions = Set(
//    "-Xfatal-warnings",
    "-feature",
    "-Yno-adapted-args",
    "-deprecation",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused:_",
    "-Ypartial-unification",
    "-Ywarn-extra-implicit",
    "-Xsource:3")

}
