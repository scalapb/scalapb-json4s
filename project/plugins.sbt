addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

addSbtPlugin("com.github.sbt" % "sbt-release" % "1.0.15")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.5")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.1")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.11"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
