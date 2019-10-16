package scalapb.json4s

import com.google.protobuf.{InvalidProtocolBufferException, Message}
import jsontest.test.{EnumTest, MyEnum}
import jsontest.test3.EnumTest3
import jsontest.test3.MyTest3.MyEnum3
import org.scalatest.{Assertion, FlatSpec, MustMatchers}
import scalapb.GeneratedMessageCompanion

class EnumFormatSpec extends FlatSpec with MustMatchers with JavaAssertions {

  "MyEnum" should "be none for non-existent int value when `ignoringUnknownFields` is set - proto2" in {
    new Parser().ignoringUnknownFields.fromJsonString[EnumTest]("""{"enum":10}""") must be(EnumTest(enum = None))
  }

  "MyEnum" should "be set to `Unrecognized` for non-existent int value - proto3" in {
    new Parser().fromJsonString[EnumTest3]("""{"enum":10}""") must be(EnumTest3(enum =  MyEnum3.Unrecognized(10)))
  }

  "EnumTest" should "fail for unknown string enum value - proto2/proto3" in {
    assertThrows[JsonFormatException](
      new Parser().fromJsonString[EnumTest]("""{"enum":"ZAZA"}""")
    )
    assertThrows[JsonFormatException](
      new Parser().fromJsonString[EnumTest3]("""{"enum":"ZAZA"}""")
    )
  }

  "EnumTest" should "not fail for unknown enum values when `ignoringUnknownFields` is set - proto2/proto3" in {
    new Parser().ignoringUnknownFields
      .fromJsonString[EnumTest]("""{"enum":"ZAZA"}""")  must be(EnumTest(enum = None))
    new Parser().ignoringUnknownFields
      .fromJsonString[EnumTest3]("""{"enum":"ZAZA"}""")  must be(EnumTest3(enum = MyEnum3.UNKNOWN))
  }

  "Enum" should "be serialized the same way as java" in {
    assertJsonIsSameAsJava(jsontest.test.EnumTest())
    assertJsonIsSameAsJava(jsontest.test.EnumTest(Some(MyEnum.V1)))
  }

  "EnumTest" should "be parsed the same way as java - non-existent string value when ignoringUnknownFields is set - proto2/proto3" in {
    assertParse[jsontest.test.EnumTest, jsontest.Test.EnumTest](
      """{"enum": "XOXO"}""",
      jsontest.Test.EnumTest.newBuilder,
      javaProto => EnumTest.fromJavaProto(javaProto))

    assertParse[EnumTest3, jsontest.Test3.EnumTest3](
      """{"enum": "XOXO"}""",
      jsontest.Test3.EnumTest3.newBuilder,
      javaProto => EnumTest3.fromJavaProto(javaProto))
  }

  "EnumTest" should "be parsed the same way as java - non-existent int value when ignoringUnknownFields is set - proto2/proto3" in {
    assertParse[jsontest.test.EnumTest, jsontest.Test.EnumTest](
      """{"enum": 10}""",
      jsontest.Test.EnumTest.newBuilder,
      javaProto => EnumTest.fromJavaProto(javaProto))

    assertParse[EnumTest3, jsontest.Test3.EnumTest3](
      """{"enum": 10}""",
      jsontest.Test3.EnumTest3.newBuilder,
      javaProto => EnumTest3.fromJavaProto(javaProto))
  }

    "EnumTest" should "be parsed the same way as java - non-existent int value when ignoringUnknownFields is NOT set - proto3" in {
      val json = """{"enum": 10}"""
      val parser = JavaJsonParser
      val builder = jsontest.Test3.EnumTest3.newBuilder
      parser.merge(json, builder)

      val scala = new Parser().fromJsonString[EnumTest3](json)
      EnumTest3.fromJavaProto(builder.build()) must be(scala)
    }

  "EnumTest" should "fail in java/scala in same way when ignoringUnknownFields is not set - proto2/proto3" in {
    val jsonWithIntEnum = """{"enum": 10}"""
    an[InvalidProtocolBufferException] must be thrownBy javaParse(jsonWithIntEnum, jsontest.Test.EnumTest.newBuilder)
    an[JsonFormatException] must be thrownBy new Parser().fromJsonString[jsontest.test.EnumTest](jsonWithIntEnum)

    val jsonWithStrEnum = """{"enum": "XOXO"}"""
    an[InvalidProtocolBufferException] must be thrownBy javaParse(jsonWithStrEnum, jsontest.Test.EnumTest.newBuilder)
    an[JsonFormatException] must be thrownBy new Parser().fromJsonString[jsontest.test.EnumTest](jsonWithStrEnum)
    an[InvalidProtocolBufferException] must be thrownBy javaParse(jsonWithStrEnum, jsontest.Test3.EnumTest3.newBuilder)
    an[JsonFormatException] must be thrownBy new Parser().fromJsonString[EnumTest3](jsonWithStrEnum)
  }

  "EnumTest" should "parse non-existent int enum in same way when ignoringUnknownFields is not set - proto3" in {
    val jsonWithIntEnum = """{"enum": 10}"""
        javaParse(jsonWithIntEnum, jsontest.Test3.EnumTest3.newBuilder)
        new Parser().fromJsonString[EnumTest3](jsonWithIntEnum)
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
