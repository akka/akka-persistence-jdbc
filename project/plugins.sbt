// compliance
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.3")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.0.1")
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.18")
addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.6.1")

// release
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.9")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
// docs
addSbtPlugin("com.lightbend.akka" % "sbt-paradox-akka" % "0.41")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.3")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.2")
