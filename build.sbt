import com.typesafe.tools.mima.core.{ IncompatibleResultTypeProblem, ProblemFilters }
import com.typesafe.tools.mima.plugin.MimaKeys.mimaBinaryIssueFilters

lazy val `akka-persistence-jdbc` = project
  .in(file("."))
  .settings(
    name := "akka-persistence-jdbc",
    libraryDependencies ++= Dependencies.Libraries,
    mimaBinaryIssueFilters ++= Seq(
        // Scala 2.11 issue which occurs because the signature of an internal lambda has changed. This lambda is not accessible outside of the method itself
        ProblemFilters.exclude[IncompatibleResultTypeProblem](
          "akka.persistence.jdbc.util.DefaultSlickDatabaseProvider#lambda#1.apply")),
    // special handling as we change organization id
    mimaPreviousArtifacts := ProjectAutoPlugin.determineMimaPreviousArtifacts(scalaBinaryVersion.value))

Global / onLoad := (Global / onLoad).value.andThen { s =>
  val v = version.value
  if (dynverGitDescribeOutput.value.hasNoTags)
    throw new MessageOnlyException(
      s"Failed to derive version from git tags. Maybe run `git fetch --unshallow`? Derived version: $v")
  s
}
