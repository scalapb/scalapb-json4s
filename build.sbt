import ReleaseTransformations._
import scalapb.compiler.Version.scalapbVersion

scalaVersion := "2.13.1"

crossScalaVersions := Seq("2.12.10", "2.13.1")

organization in ThisBuild := "com.thesamet.scalapb"

name := "bc-scalapb-json4s"

scalacOptions in ThisBuild ++= Seq("-deprecation") ++ {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target:jvm-1.7")
    case _                       => Nil
  }
}

releaseCrossBuild := true

publishTo in ThisBuild := sonatypePublishToBundle.value

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
  "org.json4s" %% "json4s-jackson" % "3.6.8",
  "org.scalatest" %% "scalatest" % "3.1.2" % "test",
  "org.scalatestplus" %% "scalacheck-1-14" % "3.1.1.1" % "test",
  "com.google.protobuf" % "protobuf-java-util" % "3.11.4" % "test",
  "com.google.protobuf" % "protobuf-java" % "3.11.4" % "protobuf"
)

lazy val root = (project in file("."))
  .settings(
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    PB.protocVersion := "-v3.11.4",
    PB.targets in Compile := Nil,
    PB.targets in Test := Seq(
      PB.gens.java -> (sourceManaged in Test).value,
      scalapb.gen(javaConversions = true) -> (sourceManaged in Test).value
    )
  )

mimaPreviousArtifacts := Set(
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.10.0"
)
