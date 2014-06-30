name := """akka-persistence-jdbc"""

version := "0.0.2"

scalaVersion := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-actor"                    % "2.3.3",
  "com.typesafe.akka"   %% "akka-persistence-experimental" % "2.3.3",
  "commons-dbcp"         % "commons-dbcp"                  % "1.4",
  "commons-codec"        % "commons-codec"                 % "1.9",
  "org.scalikejdbc"     %% "scalikejdbc"                   % "2.0.4",
  "org.slf4j"            % "slf4j-nop"                     % "1.6.4",
  "postgresql"           % "postgresql"                    % "9.1-901.jdbc4" % "test",
  "com.typesafe.akka"   %% "akka-testkit"                  % "2.3.2"         % "test",
  "org.scalatest"       %% "scalatest"                     % "2.1.6"         % "test"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")