package scalapb.json4s

import com.google.protobuf.Message
import jsontest.test.{EnumTest, MyEnum}
import jsontest.test3.EnumTest3
import jsontest.test3.MyTest3.MyEnum3
import org.scalatest.{Assertion, FlatSpec, MustMatchers}
import scalapb.GeneratedMessageCompanion

class EnumFormatSpec extends FlatSpec with MustMatchers with JavaAssertions {

  "EnumTest" should "be none for non-existent int value - proto2" in {
    new Parser().fromJsonString[EnumTest]("""{"enum":10}""") must be(EnumTest(enum = None))
  }

  "EnumTest" should "return `Unrecognized` for non-existent int value - proto3" in {
    new Parser().fromJsonString[EnumTest3]("""{"enum":10}""") must be(EnumTest3(enum =  MyEnum3.Unrecognized(10)))
  }

  "TestProto" should "fail for unknown string enum value - proto2/proto3" in {
    assertThrows[JsonFormatException](
      new Parser().fromJsonString[EnumTest]("""{"enum":"ZAZA"}""")
    )
    assertThrows[JsonFormatException](
      new Parser().fromJsonString[EnumTest3]("""{"enum":"ZAZA"}""")
    )
  }

  "TestProto" should "not fail for unknown enum values when `ignoringUnknownFields` is set - proto3/proto3" in {
    new Parser().ignoringUnknownFields
      .fromJsonString[EnumTest]("""{"enum":"ZAZA"}""")  must be(EnumTest(enum = None))
    new Parser().ignoringUnknownFields
      .fromJsonString[EnumTest3]("""{"enum":"ZAZA"}""")  must be(EnumTest3(enum = MyEnum3.UNKNOWN))
  }

  "Enum" should "be serialized the same way as java" in {
    assertJsonIsSameAsJava(jsontest.test.EnumTest())
    assertJsonIsSameAsJava(jsontest.test.EnumTest(Some(MyEnum.V1)))
  }

  "Enum" should "be parsed the same way as java - non-existent string value" in {
    assertParse[jsontest.test.EnumTest, jsontest.Test.EnumTest](
      """{"enum": "XOXO"}""",
      jsontest.Test.EnumTest.newBuilder,
      javaProto => EnumTest.fromJavaProto(javaProto))

    assertParse[EnumTest3, jsontest.Test3.EnumTest3](
      """{"enum": "XOXO"}""",
      jsontest.Test3.EnumTest3.newBuilder,
      javaProto => EnumTest3.fromJavaProto(javaProto))
  }

  "Enum" should "be parsed the same way as java - non-existent int value" in {
    assertParse[jsontest.test.EnumTest, jsontest.Test.EnumTest](
      """{"enum": 10}""",
      jsontest.Test.EnumTest.newBuilder,
      javaProto => EnumTest.fromJavaProto(javaProto))

    assertParse[EnumTest3, jsontest.Test3.EnumTest3](
      """{"enum": 10}""",
      jsontest.Test3.EnumTest3.newBuilder,
      javaProto => EnumTest3.fromJavaProto(javaProto))
  }

  def assertParse[T <: scalapb.GeneratedMessage with scalapb.Message[T], J](
                                                                          json: String,
                                                                          builder: Message.Builder,
                                                                          fromJavaProto: J => T)(
    implicit cmp: GeneratedMessageCompanion[T]): Assertion = {
    val parser = JavaJsonParser.ignoringUnknownFields()
    parser.merge(json, builder)

    val scala = new Parser().ignoringUnknownFields.fromJsonString[T](json)

    fromJavaProto(builder.build().asInstanceOf[J]) must be(scala)
  }

}
