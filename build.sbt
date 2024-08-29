import scalapb.compiler.Version.scalapbVersion

scalaVersion := "2.13.14"

crossScalaVersions := Seq("2.12.19", "2.13.14", "3.4.3")

ThisBuild / organization := "com.thesamet.scalapb"

name := "scalapb-json4s"

ThisBuild / scalacOptions ++= Seq("-deprecation") ++ {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target:jvm-1.7")
    case _                       => Nil
  }
}

ThisBuild / publishTo := sonatypePublishToBundle.value

val protobufJava = "com.google.protobuf" % "protobuf-java" % "4.28.0"

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf,test",
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.18.0" % "test",
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
