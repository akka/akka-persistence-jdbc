import bintray.Plugin._

net.virtualvoid.sbt.graph.Plugin.graphSettings

bintraySettings

organization := "com.github.dnvriend"

name := "akka-persistence-jdbc"

version := "1.1.3"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.5", "2.11.6")

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++= {
    val akkaVersion = "2.3.9"
    Seq(
    "com.typesafe.akka"   %% "akka-actor"                    % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence-experimental" % akkaVersion,
    "org.scalikejdbc"     %% "scalikejdbc"                   % "2.2.5",
    "ch.qos.logback"       % "logback-classic"               % "1.1.2"           % Test,
    "com.typesafe.akka"   %% "akka-slf4j"                    % akkaVersion       % Test,
    "ch.qos.logback"       % "logback-classic"               % "1.1.2"           % Test,
    "org.postgresql"       % "postgresql"                    % "9.4-1201-jdbc41" % Test,
    "com.h2database"       % "h2"                            % "1.4.181"         % Test,
    "mysql"                % "mysql-connector-java"          % "5.1.33"          % Test,
    "com.typesafe.akka"   %% "akka-testkit"                  % akkaVersion       % Test,
    "org.scalatest"       %% "scalatest"                     % "2.1.4"           % Test,
    "org.pegdown"          % "pegdown"                       % "1.4.2"           % Test,
    "com.github.krasserm" %% "akka-persistence-testkit"      % "0.3.4"           % Test
  )
}

autoCompilerPlugins := true

parallelExecution in Test := false

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

publishMavenStyle := true

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("akka", "jdbc", "persistence")

bintray.Keys.packageAttributes in bintray.Keys.bintray ~=
  ((_: bintray.AttrMap) ++ Map("website_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc")), "github_repo" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc.git")), "issue_tracker_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc/issues/"))))

//testOptions in ThisBuild += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports")
