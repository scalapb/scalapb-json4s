package com.trueaccord.scalapb.json

import com.google.protobuf.util.JsonFormat.TypeRegistry
import com.trueaccord.scalapb.json.JsonFormat.GenericCompanion
import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, JavaProtoSupport, Message}
import org.scalatest.MustMatchers

trait JavaAssertions {
  self: MustMatchers =>

  def registeredCompanions: Seq[GenericCompanion] = Seq.empty

  val JavaJsonTypeRegistry = registeredCompanions.foldLeft(TypeRegistry.newBuilder())(_ add _.javaDescriptor).build()
  val JavaJsonPrinter = com.google.protobuf.util.JsonFormat.printer().usingTypeRegistry(JavaJsonTypeRegistry)
  val JavaJsonParser = com.google.protobuf.util.JsonFormat.parser()

  val ScalaFormatRegistry = JsonFormat.DefaultRegistry.registerCompanions(registeredCompanions)
  val ScalaJsonParser = new Parser(formatRegistry = ScalaFormatRegistry)
  val ScalaJsonPrinter = new Printer(formatRegistry = ScalaFormatRegistry)

  def assertJsonIsSameAsJava[T <: GeneratedMessage with Message[T]](v: T)(
    implicit cmp: GeneratedMessageCompanion[T]) = {
    val scalaJson = ScalaJsonPrinter.print(v)
    val javaJson = JavaJsonPrinter.print(
      cmp.asInstanceOf[JavaProtoSupport[T, com.google.protobuf.GeneratedMessageV3]].toJavaProto(v))

    import org.json4s.jackson.JsonMethods._
    parse(scalaJson) must be (parse(javaJson))
    ScalaJsonParser.fromJsonString[T](scalaJson) must be(v)
  }

  def javaParse[T <: com.google.protobuf.GeneratedMessageV3.Builder[T]](json: String, b: com.google.protobuf.GeneratedMessageV3.Builder[T]) = {
    JavaJsonParser.merge(json, b)
    b.build()
  }
}
