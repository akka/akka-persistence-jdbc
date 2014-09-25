import bintray.Plugin._

seq(bintraySettings:_*)

organization := "com.github.dnvriend"

name := "akka-persistence-jdbc"

version := "1.0.8"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.10.4", "2.11.2")

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++= {
    val akkaVersion = "2.3.6"
    Seq(    
    "com.typesafe.akka"   %% "akka-actor"                    % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence-experimental" % akkaVersion,
    "commons-codec"        % "commons-codec"                 % "1.9",
    "org.scalikejdbc"     %% "scalikejdbc"                   % "2.1.2",
    "ch.qos.logback"       % "logback-classic"               % "1.1.2"           % "test",
    "com.typesafe.akka"   %% "akka-slf4j"                    % akkaVersion       % "test",
    "ch.qos.logback"       % "logback-classic"               % "1.1.2"           % "test",
    "org.postgresql"       % "postgresql"                    % "9.3-1102-jdbc41" % "test",   
    "com.h2database"       % "h2"                            % "1.4.181"         % "test",
    "mysql"                % "mysql-connector-java"          % "5.1.33"          % "test",
    "com.typesafe.akka"   %% "akka-testkit"                  % akkaVersion       % "test",
    "org.scalatest"       %% "scalatest"                     % "2.1.4"           % "test",
    "com.github.krasserm" %% "akka-persistence-testkit"      % "0.3.4"           % "test"
  )
}

autoCompilerPlugins := true

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

publishMavenStyle := true

net.virtualvoid.sbt.graph.Plugin.graphSettings

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("akka", "jdbc", "persistence")

bintray.Keys.packageAttributes in bintray.Keys.bintray ~=
  ((_: bintray.AttrMap) ++ Map("website_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc")), "github_repo" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc.git")), "issue_tracker_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc/issues/"))))
