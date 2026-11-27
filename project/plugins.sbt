addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.4.0")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.1.0-RC3")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "1.0.0-editions-local"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.2.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.1")
