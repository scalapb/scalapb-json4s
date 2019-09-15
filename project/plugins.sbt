addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.11")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.5")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.25")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.0"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.6.0")
