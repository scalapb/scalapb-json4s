import ReleaseTransformations._
import scalapb.compiler.Version.scalapbVersion

scalaVersion := "2.13.6"

crossScalaVersions := Seq("2.12.14", "2.13.6")

ThisBuild / organization := "com.thesamet.scalapb"

name := "scalapb-json4s"

ThisBuild / scalacOptions ++= Seq("-deprecation") ++ {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target:jvm-1.7")
    case _                       => Nil
  }
}

releaseCrossBuild := true

ThisBuild / publishTo := sonatypePublishToBundle.value

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand(s"sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf,test",
  "org.scalatest" %% "scalatest" % "3.2.9" % "test",
  "org.scalatestplus" %% "scalacheck-1-15" % "3.2.9.0" % "test",
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.4",
  "com.google.protobuf" % "protobuf-java-util" % "3.17.3" % "test",
  "com.google.protobuf" % "protobuf-java" % "3.17.3" % "protobuf",
  "org.json4s" %% "json4s-jackson" % "4.0.0"
)

lazy val root = (project in file("."))
  .settings(
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    PB.protocVersion := "3.11.4",
    Compile / PB.targets := Nil,
    Test / PB.targets := Seq(
      PB.gens.java -> (Test / sourceManaged).value,
      scalapb.gen(javaConversions = true) -> (Test / sourceManaged).value
    )
  )

mimaPreviousArtifacts := Set(
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.10.0"
)
