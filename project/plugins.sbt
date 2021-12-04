addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.4")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.6"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.0.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.5")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
