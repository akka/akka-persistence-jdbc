name := """akka-persistence-jdbc"""

version := "1.0.0"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.10.4", "2.11.1")

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies ++= {
    val akkaVersion = "2.3.4"
    Seq(
    "com.typesafe.akka"   %% "akka-actor"                    % akkaVersion,
    "com.typesafe.akka"   %% "akka-persistence-experimental" % akkaVersion,
    "commons-dbcp"         % "commons-dbcp"                  % "1.4",
    "commons-codec"        % "commons-codec"                 % "1.9",
    "commons-io"           % "commons-io"                    % "2.4",
    "org.scalikejdbc"     %% "scalikejdbc"                   % "2.0.4",
    "org.slf4j"            % "slf4j-nop"                     % "1.6.4",
    "postgresql"           % "postgresql"                    % "9.1-901.jdbc4" % "test",
    "com.h2database"       % "h2"                            % "1.4.179"       % "test",
    "mysql"                % "mysql-connector-java"          % "5.1.31"        % "test",
    "com.typesafe.akka"   %% "akka-testkit"                  % akkaVersion     % "test",
    "org.scalatest"       %% "scalatest"                     % "2.1.4"         % "test"
  )
}

libraryDependencies += "com.github.krasserm" %% "akka-persistence-testkit" % "0.3.3" % "test"

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/dnvriend/akka-persistence-jdbc</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com/dnvriend/akka-persistence-jdbc.git</url>
    <connection>scm:git:git@github.com/dnvriend/akka-persistence-jdbc.git</connection>
  </scm>
  <developers>
    <developer>
      <id>you</id>
      <name>Dennis Vriend</name>
      <url>https://github.com/dnvriend</url>
    </developer>
  </developers>
)