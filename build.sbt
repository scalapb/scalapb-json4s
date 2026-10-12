import scalapb.compiler.Version.scalapbVersion

scalaVersion := "2.13.18"

crossScalaVersions := Seq("2.12.21", "2.13.18", "3.3.8")

ThisBuild / organization := "com.thesamet.scalapb"

name := "scalapb-json4s"

ThisBuild / scalacOptions ++= Seq("-deprecation")

val javaRelease = settingKey[String]("Minimum Java runtime and API version")
javaRelease := (if (scalaBinaryVersion.value == "3") "11" else "8")
scalacOptions += s"-release:${javaRelease.value}"
javacOptions ++= Seq("--release", javaRelease.value)

val protobufJava = "com.google.protobuf" % "protobuf-java" % "4.36.2"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf,test",
  "org.scalatest" %% "scalatest" % "3.2.20" % "test",
  "org.scalatestplus" %% "scalacheck-1-19" % "3.2.20.0" % "test",
  "com.google.protobuf" % "protobuf-java-util" % protobufJava.revision % "test",
  protobufJava % "protobuf",
  "io.github.json4s" %% "json4s-jackson-core" % "4.1.1"
)

lazy val root = (project in file("."))
  .settings(
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    PB.protocVersion := protobufJava.revision,
    Compile / PB.targets := Nil,
    Test / PB.targets := Seq(
      PB.gens.java -> (Test / sourceManaged).value,
      scalapb.gen(javaConversions = true) -> (Test / sourceManaged).value
    ),
    compileOrder := CompileOrder.JavaThenScala
  )

// Last published release on master's 1.0 line (available for every Scala version).
mimaPreviousArtifacts := Set(
  "com.thesamet.scalapb" %% "scalapb-json4s" % "1.0.0-alpha.1"
)
