name := """akka-persistence-jdbc"""

version := "0.0.6"

scalaVersion := "2.10.4"

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