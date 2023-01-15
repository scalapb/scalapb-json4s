import scalapb.compiler.Version.scalapbVersion

scalaVersion := "2.13.10"

crossScalaVersions := Seq("2.12.17", "2.13.10", "3.2.1")

ThisBuild / version := "0.12.1-SNAPSHOT"

ThisBuild / organization := "com.thesamet.scalapb"

name := "scalapb-json4s"

ThisBuild / scalacOptions ++= Seq("-deprecation") ++ {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target:jvm-1.7")
    case _                       => Nil
  }
}

ThisBuild / publishTo := sonatypePublishToBundle.value

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf,test",
  "org.scalatest" %% "scalatest" % "3.2.15" % "test",
  "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0" % "test",
  "com.google.protobuf" % "protobuf-java-util" % "3.21.12" % "test",
  "com.google.protobuf" % "protobuf-java" % "3.21.12" % "protobuf",
  "org.json4s" %% "json4s-jackson-core" % "4.0.6"
)

lazy val root = (project in file("."))
  .settings(
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    PB.protocVersion := "3.11.4",
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
