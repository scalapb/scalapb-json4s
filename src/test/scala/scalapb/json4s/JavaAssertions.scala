package scalapb.json4s

import com.google.protobuf.util.JsonFormat.{TypeRegistry => JavaTypeRegistry}
import org.scalatest.{FlatSpec, MustMatchers, Assertion}
import scalapb.json4s.JsonFormat.GenericCompanion
import com.google.protobuf.{GeneratedMessageV3, InvalidProtocolBufferException}
import scalapb.{
  GeneratedMessage,
  GeneratedMessageCompanion,
  JavaProtoSupport,
  Message
}
import com.google.protobuf.util.JsonFormat.{Parser => JavaJsonParser}
import com.google.protobuf.util.{JsonFormat => JavaJsonFormat}

import scala.language.existentials

case class ParserContext(scalaParser: Parser, javaParser: JavaJsonParser)

class DefaultParserContext {
  implicit val pc: ParserContext =
    ParserContext(new Parser(), JavaJsonFormat.parser())
}

class IgnoringUnknownParserContext {
  implicit val pc: ParserContext = ParserContext(
    new Parser().ignoringUnknownFields,
    JavaJsonFormat.parser.ignoringUnknownFields()
  )
}

trait JavaAssertions {
  self: FlatSpec with MustMatchers =>

  def registeredCompanions: Seq[GeneratedMessageCompanion[_]] = Seq.empty

  val JavaJsonTypeRegistry = registeredCompanions
    .foldLeft(JavaTypeRegistry.newBuilder())(_ add _.javaDescriptor)
    .build()
  val JavaJsonPrinter = com.google.protobuf.util.JsonFormat
    .printer()
    .usingTypeRegistry(JavaJsonTypeRegistry)
  val JavaJsonParser = com.google.protobuf.util.JsonFormat.parser()

  val ScalaTypeRegistry = registeredCompanions.foldLeft(TypeRegistry.empty)(
    (r, c) => r.addMessageByCompanion(c.asInstanceOf[GenericCompanion])
  )
  val ScalaJsonParser = new Parser().withTypeRegistry(ScalaTypeRegistry)
  val ScalaJsonPrinter =
    new Printer().withTypeRegistry(typeRegistry = ScalaTypeRegistry)

  def assertJsonIsSameAsJava[T <: GeneratedMessage with Message[T]](
      v: T,
      checkRoundtrip: Boolean = true
  )(implicit cmp: GeneratedMessageCompanion[T]) = {
    val scalaJson = ScalaJsonPrinter.print(v)
    val javaJson = JavaJsonPrinter.print(
      cmp
        .asInstanceOf[
          JavaProtoSupport[T, com.google.protobuf.GeneratedMessageV3]
        ]
        .toJavaProto(v)
    )

    import org.json4s.jackson.JsonMethods._
    parse(scalaJson) must be(parse(javaJson))
    if (checkRoundtrip) {
      ScalaJsonParser.fromJsonString[T](scalaJson) must be(v)
    }
  }

  def javaFormat[T <: com.google.protobuf.GeneratedMessageV3.Builder[T]](
      json: String,
      b: com.google.protobuf.GeneratedMessageV3.Builder[T]
  ) = {
    JavaJsonParser.merge(json, b)
    b.build()
  }

  def assertParse[T <: scalapb.GeneratedMessage with scalapb.Message[T], J <: GeneratedMessageV3](
      json: String,
      expected: T
  )(
      implicit cmp: GeneratedMessageCompanion[T] with JavaProtoSupport[T, J],
      parserContext: ParserContext
  ): Assertion = {
    val parsedJava: J = {
      val builder = cmp.toJavaProto(cmp.defaultInstance).newBuilderForType()
      parserContext.javaParser.merge(json, builder)
      builder.build().asInstanceOf[J]
    }

    val parsedScala = parserContext.scalaParser.fromJsonString[T](json)(cmp)
    parsedScala must be(expected)
    cmp.fromJavaProto(parsedJava) must be(expected)
  }

  def assertFails[T <: scalapb.GeneratedMessage with scalapb.Message[T], J <: GeneratedMessageV3](
      json: String,
      cmp: GeneratedMessageCompanion[T] with JavaProtoSupport[T, J]
  )(implicit parserContext: ParserContext): Assertion = {
    val builder = cmp.toJavaProto(cmp.defaultInstance).newBuilderForType()
    assertThrows[InvalidProtocolBufferException] {
      parserContext.javaParser.merge(json, builder)
    }
    assertThrows[JsonFormatException] {
      parserContext.scalaParser.fromJsonString[T](json)(cmp)
    }
  }
}
