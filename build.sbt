import com.lightbend.paradox.apidoc.ApidocPlugin.autoImport.apidocRootPackage
import com.typesafe.tools.mima.core.{ IncompatibleResultTypeProblem, ProblemFilters }
import com.typesafe.tools.mima.plugin.MimaKeys.mimaBinaryIssueFilters

lazy val `akka-persistence-jdbc` = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .disablePlugins(MimaPlugin, SitePlugin)
  .aggregate(core, docs)
  .settings(publish / skip := true)

lazy val core = project
  .in(file("core"))
  .enablePlugins(Publish)
  .disablePlugins(SitePlugin)
  .settings(
    name := "akka-persistence-jdbc",
    libraryDependencies ++= Dependencies.Libraries,
    mimaBinaryIssueFilters ++= Seq(
        // Scala 2.11 issue which occurs because the signature of an internal lambda has changed. This lambda is not accessible outside of the method itself
        ProblemFilters.exclude[IncompatibleResultTypeProblem](
          "akka.persistence.jdbc.util.DefaultSlickDatabaseProvider#lambda#1.apply")),
    // special handling as we change organization id
    mimaPreviousArtifacts := ProjectAutoPlugin.determineMimaPreviousArtifacts(scalaBinaryVersion.value))

lazy val migration = project
  .in(file("migration"))
  .enablePlugins(Publish)
  .disablePlugins(SitePlugin, MimaPlugin)
  .settings(
      name := "akka-persistence-jdbc-migration",
      libraryDependencies ++= Dependencies.Migration)

lazy val docs = project
  .enablePlugins(ProjectAutoPlugin, AkkaParadoxPlugin, ParadoxSitePlugin, PreprocessPlugin, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := "Akka Persistence JDBC",
    publish / skip := true,
    makeSite := makeSite.dependsOn(LocalRootProject / ScalaUnidoc / doc).value,
    previewPath := (Paradox / siteSubdirName).value,
    Preprocess / siteSubdirName := s"api/akka-persistence-jdbc/${if (isSnapshot.value) "snapshot"
      else version.value}",
    Preprocess / sourceDirectory := (LocalRootProject / ScalaUnidoc / unidoc / target).value,
    Paradox / siteSubdirName := s"docs/akka-persistence-jdbc/${if (isSnapshot.value) "snapshot" else version.value}",
    paradoxProperties ++= Map(
        "akka.version" -> Dependencies.AkkaVersion,
        "extref.github.base_url" -> s"https://github.com/akka/akka-persistence-jdbc/blob/${if (isSnapshot.value) "master"
        else "v" + version.value}/%s",
        // Akka
        "extref.akka.base_url" -> s"https://doc.akka.io/docs/akka/${Dependencies.AkkaBinaryVersion}/%s",
        "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${Dependencies.AkkaBinaryVersion}/",
        "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${Dependencies.AkkaBinaryVersion}/",
        // Java
        "javadoc.base_url" -> "https://docs.oracle.com/javase/8/docs/api/",
        // Scala
        "scaladoc.scala.base_url" -> s"https://www.scala-lang.org/api/${scalaBinaryVersion.value}.x/",
        "scaladoc.akka.persistence.jdbc.base_url" -> s"/${(Preprocess / siteSubdirName).value}/"),
    paradoxGroups := Map("Language" -> Seq("Java", "Scala")),
    resolvers += Resolver.jcenterRepo,
    publishRsyncArtifact := makeSite.value -> "www/",
    publishRsyncHost := "akkarepo@gustav.akka.io",
    apidocRootPackage := "akka")

Global / onLoad := (Global / onLoad).value.andThen { s =>
  val v = version.value
  if (dynverGitDescribeOutput.value.hasNoTags)
    throw new MessageOnlyException(
      s"Failed to derive version from git tags. Maybe run `git fetch --unshallow`? Derived version: $v")
  s
}
