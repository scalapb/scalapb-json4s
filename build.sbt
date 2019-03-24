import ReleaseTransformations._
import scalapb.compiler.Version.scalapbVersion

scalaVersion := "2.11.12"

crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0-M5")

organization in ThisBuild := "com.thesamet.scalapb"

name := "scalapb-json4s"

scalacOptions in ThisBuild ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target:jvm-1.7")
    case _ => Nil
  }
}

releaseCrossBuild := true

publishTo := sonatypePublishTo.value

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
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges,
)

libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf,test",
  "org.json4s" %% "json4s-jackson" % "3.6.5",
  "org.scalatest" %% "scalatest" % "3.0.6" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test",
  "com.google.protobuf" % "protobuf-java-util" % "3.7.0" % "test",
  "com.google.protobuf" % "protobuf-java" % "3.7.0" % "protobuf",
)

lazy val Proto26Test = config("proto26") extend(Test)

lazy val root = (project in file("."))
  .configs(Proto26Test)
  .settings(
    inConfig(Proto26Test)(
      Defaults.testSettings ++
      sbtprotoc.ProtocPlugin.protobufConfigSettings
    ),
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings)
  )

PB.protocVersion in Proto26Test := "-v261"

PB.protoSources in Proto26Test := Seq((sourceDirectory in Proto26Test).value / "protobuf")

PB.targets in Compile := Nil

PB.targets in Test := Seq(
  PB.gens.java -> (sourceManaged in Test).value,
  scalapb.gen(javaConversions=true) -> (sourceManaged in Test).value
)

PB.targets in Proto26Test := Seq(
  scalapb.gen() -> (sourceManaged in Proto26Test).value
)

mimaPreviousArtifacts := Set("com.thesamet.scalapb" %% "scalapb-json4s" % "0.7.0")
