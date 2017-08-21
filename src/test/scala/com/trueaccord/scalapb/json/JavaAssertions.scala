package com.trueaccord.scalapb.json

import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, JavaProtoSupport, Message}
import org.scalatest.MustMatchers

trait JavaAssertions {
  self: MustMatchers =>

  def javaJsonTypeRegistry = com.google.protobuf.util.JsonFormat.TypeRegistry.getEmptyTypeRegistry
  val JavaJsonPrinter = com.google.protobuf.util.JsonFormat.printer().usingTypeRegistry(javaJsonTypeRegistry)
  val JavaJsonParser = com.google.protobuf.util.JsonFormat.parser()

  def assertJsonIsSameAsJava[T <: GeneratedMessage with Message[T]](v: T)(
    implicit cmp: GeneratedMessageCompanion[T]) = {
    val scalaJson = com.trueaccord.scalapb.json.JsonFormat.toJsonString(v)
    val javaJson = JavaJsonPrinter.print(
      cmp.asInstanceOf[JavaProtoSupport[T, com.google.protobuf.GeneratedMessageV3]].toJavaProto(v))

    import org.json4s.jackson.JsonMethods._
    parse(scalaJson) must be (parse(javaJson))
    JsonFormat.parser.fromJsonString[T](scalaJson) must be(v)
  }

  def javaParse[T <: com.google.protobuf.GeneratedMessageV3.Builder[T]](json: String, b: com.google.protobuf.GeneratedMessageV3.Builder[T]) = {
    JavaJsonParser.merge(json, b)
    b.build()
  }
}
