name := """akka-persistence-jdbc"""

version := "0.0.1"

scalaVersion := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-actor"                    % "2.3.2",
  "com.typesafe.akka"   %% "akka-persistence-experimental" % "2.3.2",
  "com.jsuereth"         % "scala-arm_2.10"                % "1.3",
  "commons-dbcp"         % "commons-dbcp"                  % "1.4",
  "commons-codec"        % "commons-codec"                 % "1.9",
  "com.github.mauricio" %% "postgresql-async"              % "0.2.12",
  "postgresql"           % "postgresql"                    % "9.1-901.jdbc4",
  "com.h2database"       % "h2"                            % "1.3.175",
  "com.typesafe.akka"   %% "akka-testkit"                  % "2.3.2"    % "test",
  "org.scalatest"       %% "scalatest"                     % "2.1.6"    % "test"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")