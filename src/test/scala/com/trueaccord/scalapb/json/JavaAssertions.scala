package com.trueaccord.scalapb.json

import com.google.protobuf.util.JsonFormat.{TypeRegistry => JavaTypeRegistry}
import com.trueaccord.scalapb.json.JsonFormat.GenericCompanion
import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, JavaProtoSupport, Message}
import org.scalatest.MustMatchers
import scala.language.existentials

trait JavaAssertions {
  self: MustMatchers =>

  def registeredCompanions: Seq[GeneratedMessageCompanion[_]] = Seq.empty

  val JavaJsonTypeRegistry = registeredCompanions.foldLeft(JavaTypeRegistry.newBuilder())(_ add _.javaDescriptor).build()
  val JavaJsonPrinter = com.google.protobuf.util.JsonFormat.printer().usingTypeRegistry(JavaJsonTypeRegistry)
  val JavaJsonParser = com.google.protobuf.util.JsonFormat.parser()

  val ScalaTypeRegistry = registeredCompanions.foldLeft(TypeRegistry.empty)((r, c) => r.addMessageByCompanion(c.asInstanceOf[GenericCompanion]))
  val ScalaJsonParser = new Parser(typeRegistry = ScalaTypeRegistry)
  val ScalaJsonPrinter = new Printer(typeRegistry = ScalaTypeRegistry)

  def assertJsonIsSameAsJava[T <: GeneratedMessage with Message[T]](v: T, checkRoundtrip: Boolean = true)(
    implicit cmp: GeneratedMessageCompanion[T]) = {
    val scalaJson = ScalaJsonPrinter.print(v)
    val javaJson = JavaJsonPrinter.print(
      cmp.asInstanceOf[JavaProtoSupport[T, com.google.protobuf.GeneratedMessageV3]].toJavaProto(v))

    import org.json4s.jackson.JsonMethods._
    parse(scalaJson) must be (parse(javaJson))
    if(checkRoundtrip) {
      ScalaJsonParser.fromJsonString[T](scalaJson) must be(v)
    }
  }

  def javaParse[T <: com.google.protobuf.GeneratedMessageV3.Builder[T]](json: String, b: com.google.protobuf.GeneratedMessageV3.Builder[T]) = {
    JavaJsonParser.merge(json, b)
    b.build()
  }
}
