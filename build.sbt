/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

name := "akka-persistence-jdbc"

organization := "com.github.dnvriend"

version := "2.2.20-SNAPSHOT"

isSnapshot := true

resolvers += Resolver.typesafeRepo("releases")

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaVersion = "2.4.4"
  val slickVersion = "3.1.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.slick" %% "slick" % slickVersion,
    "com.typesafe.slick" %% "slick-extensions" % "3.1.0",
    "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion % Test,
    "ch.qos.logback" % "logback-classic" % "1.1.3" % Test,
    "com.typesafe.akka" %% "akka-persistence-tck" % akkaVersion % Test,
    "org.postgresql" % "postgresql" % "9.4-1206-jdbc42" % Test,
    "mysql" % "mysql-connector-java" % "5.1.33" % Test,
    "com.h2database" % "h2" % "1.4.191" % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "org.scalatest" %% "scalatest" % "2.2.5" % Test,
    "org.scalacheck" %% "scalacheck" % "1.12.5" % Test
  )
}

fork in Test := true

parallelExecution in Test := false

licenses +=("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))

// enable scala code formatting //
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform

// Scalariform settings
SbtScalariform.autoImport.scalariformPreferences := SbtScalariform.autoImport.scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(RewriteArrowSymbols, true)

// enable updating file headers //
import de.heikoseeberger.sbtheader.license.Apache2_0

headers := Map(
  "scala" -> Apache2_0("2016", "Dennis Vriend"),
  "conf" -> Apache2_0("2016", "Dennis Vriend", "#")
)

// build info configuration //
buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, buildInfoBuildNumber)

buildInfoPackage := "akka.persistence.jdbc"

enablePlugins(AutomateHeaderPlugin, BuildInfoPlugin)
