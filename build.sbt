import ReleaseTransformations._
import com.trueaccord.scalapb.compiler.Version.{protobufVersion, scalapbVersion}

scalaVersion := "2.11.8"

crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1")

organization in ThisBuild := "com.trueaccord.scalapb"

name in ThisBuild := "scalapb-json4s"

scalacOptions in ThisBuild ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target:jvm-1.7")
    case _ => Nil
  }
}

releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  pushChanges,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true)
)

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "scalapb-runtime" % scalapbVersion,
  "org.json4s" %% "json4s-jackson" % "3.5.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.google.protobuf" % "protobuf-java-util" % protobufVersion % "test",
  "com.google.protobuf" % "protobuf-java" % protobufVersion % "protobuf"
)

Project.inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings)

PB.targets in Compile := Nil

PB.targets in Test := Seq(
  PB.gens.java -> (sourceManaged in Test).value,
  scalapb.gen(javaConversions=true) -> (sourceManaged in Test).value
)
