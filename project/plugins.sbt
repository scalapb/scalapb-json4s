addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.4")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.34")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.8"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
