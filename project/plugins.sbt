addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

addSbtPlugin("com.github.sbt" % "sbt-release" % "1.0.15")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.7")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.4")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.4"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.9.2")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
