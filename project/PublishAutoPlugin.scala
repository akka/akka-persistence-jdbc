import sbt._
import sbt.Keys._
import bintray.BintrayKeys._

object PublishAutoPlugin extends AutoPlugin { 

  override val trigger: PluginTrigger = allRequirements

  override val requires: Plugins = sbtrelease.ReleasePlugin

  object autoImport {
  }

 import autoImport._

 override val projectSettings = Seq(
    publishMavenStyle := true,
    pomExtraSetting("akka-persistence-jdbc"),
    homepageSetting("akka-persistence-jdbc"),
    bintrayPackageLabelsSettings("jdbc"),
    bintrayPackageAttributesSettings("akka-persistence-jdbc")
 )
  
def pomExtraSetting(name: String) = pomExtra := 
    <scm>
        <url>https://github.com/dnvriend/${name}</url>
        <connection>scm:git@github.com:dnvriend/${name}.git</connection>
        </scm>
        <developers>
        <developer>
            <id>dnvriend</id>
            <name>Dennis Vriend</name>
            <url>https://github.com/dnvriend</url>
        </developer>
        </developers>

    def homepageSetting(name: String) = 
      homepage := Some(url(s"https://github.com/dnvriend/$name"))

    def bintrayPackageLabelsSettings(labels: String*) = 
	  bintrayPackageLabels := Seq("akka", "persistence") ++ labels

    def bintrayPackageAttributesSettings(name: String) = bintrayPackageAttributes ~=
	  (_ ++ Map(
	    "website_url" -> Seq(bintry.Attr.String(s"https://github.com/dnvriend/$name")),
	    "github_repo" -> Seq(bintry.Attr.String(s"https://github.com/dnvriend/$name.git")),
	    "issue_tracker_url" -> Seq(bintry.Attr.String(s"https://github.com/dnvriend/$name.git/issues/"))
	  )
)
}
