addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.11")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.8")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.25")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.1"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.6.1")
