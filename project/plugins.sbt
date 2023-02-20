// compliance
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.9.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.8.0")

// release
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.11")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
// docs
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.45")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.2")
