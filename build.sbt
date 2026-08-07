import scalapb.compiler.Version.scalapbVersion

scalaVersion := "2.13.18"

crossScalaVersions := Seq("2.12.21", "2.13.18", "3.3.8")

ThisBuild / organization := "com.thesamet.scalapb"

name := "scalapb-json4s"

ThisBuild / scalacOptions ++= Seq("-deprecation")

val protobufJava = "com.google.protobuf" % "protobuf-java" % "3.25.9"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf,test",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  "org.scalatestplus" %% "scalacheck-1-19" % "3.2.20.0" % "test",
  "com.google.protobuf" % "protobuf-java-util" % protobufJava.revision % "test",
  protobufJava % "protobuf",
  "org.json4s" %% "json4s-jackson-core" % "4.0.7"
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

mimaPreviousArtifacts := Set(
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.11.0"
)
