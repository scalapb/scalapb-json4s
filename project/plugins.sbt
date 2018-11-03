addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.10")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.19")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.8.2"

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.3.0")
