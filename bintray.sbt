import bintray.Plugin._

bintray.Plugin.bintraySettings

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("akka", "jdbc", "persistence")

bintray.Keys.packageAttributes in bintray.Keys.bintray ~=
  ((_: bintray.AttrMap) ++ Map("website_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc")), "github_repo" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc.git")), "issue_tracker_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc/issues/"))))

publishMavenStyle := true