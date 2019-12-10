import sbt._
import sbt.Keys._

object Publish extends AutoPlugin {
  import bintray.BintrayPlugin
  import bintray.BintrayKeys._

  override def trigger = allRequirements
  override def requires = BintrayPlugin

  override val projectSettings = Seq(
    bintrayOrganization := Some("akka"),
    bintrayPackage := "akka-persistence-jdbc",
    bintrayRepository := (if (isSnapshot.value) "snapshots" else "maven"),
    publishMavenStyle := true,
    bintrayPackageLabels := Seq("akka", "persistence", "jdbc"))
}
