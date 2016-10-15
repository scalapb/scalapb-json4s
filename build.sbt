import ReleaseTransformations._

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.11.7", "2.10.5")

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

val scalaPbVersion = "0.5.39"

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "scalapb-runtime" % scalaPbVersion,
  "org.json4s" %% "json4s-jackson" % "3.4.0",
  "com.trueaccord.scalapb" %% "scalapb-runtime" % "0.5.42" % "protobuf",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)

PB.protoSources in Compile := Seq(
  (sourceDirectory in Test)(_ / "/protobuf").value
)

PB.targets in Compile := Seq(
  scalapb.gen(grpc=false) -> (sourceDirectory in Test)(_ / "/scala").value
)