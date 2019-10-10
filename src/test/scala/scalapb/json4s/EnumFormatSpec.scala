package scalapb.json4s

import jsontest.test.{EnumTest, MyEnum, MyEnumWithoutDefault, MyTest}
import org.scalatest.{FlatSpec, MustMatchers}

class EnumFormatSpec extends FlatSpec with MustMatchers with JavaAssertions {

  "TestProto" should "infer default value when present" in {
    new Parser().fromJsonString[EnumTest]("""{"enum":10}""") must be(EnumTest(enum = Some(MyEnum.Unrecognized(10))))
  }

  "TestProto" should "return `Unrecognized` when 0-index enum is not present" in {
    new Parser().fromJsonString[EnumTest]("""{"enum_no_default":10}""") must be(EnumTest(enumNoDefault = Some(MyEnumWithoutDefault.Unrecognized(10))))
  }

  "TestProto" should "fail for unknown string enum value" in {
    assertThrows[JsonFormatException](
      new Parser().fromJsonString[EnumTest]("""{"enum":"ZAZA"}""")
    )
  }

  "TestProto" should "not fail for unknown enum values when `ignoringUnknownFields` is set" in {
    new Parser().ignoringUnknownFields
      .fromJsonString[EnumTest]("""{"enum":"ZAZA"}""")  must be(EnumTest(enum = None))
  }


  "Enum" should "be serialized the same way as java" in {
    assertJsonIsSameAsJava(jsontest.test.EnumTest())
    assertJsonIsSameAsJava(jsontest.test.EnumTest(Some(MyEnum.V1)))
  }

  "Enum" should "be parsed the same way as java - non-existent string value" in {
    val parser = JavaJsonParser.ignoringUnknownFields()
    val enumJson = """{"enum": "XOXO"}"""

    javaParse(enumJson, jsontest.Test.EnumTest.newBuilder, parser).toString must be("")
    new Parser().ignoringUnknownFields.fromJsonString[jsontest.test.EnumTest](enumJson) must be(
      jsontest.test.EnumTest(None)
    )
  }

  "Enum" should "be parsed the same way as java - non-existent int value" in {
    val parser = JavaJsonParser.ignoringUnknownFields()
    val enumJson = """{"enum": 10}"""

    javaParse(enumJson, jsontest.Test.EnumTest.newBuilder, parser).toString must be("")
    new Parser().ignoringUnknownFields.fromJsonString[jsontest.test.EnumTest](enumJson) must be(
      jsontest.test.EnumTest(Some(MyEnum.Unrecognized(10)))
    )
  }

}
