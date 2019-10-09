package scalapb.json4s

import jsontest.test.MyEnum
import org.scalatest.{FlatSpec, MustMatchers}

class EnumFormatSpec extends FlatSpec with MustMatchers with JavaAssertions {

  "Enum" should "be serialized the same way as java" in {
    assertJsonIsSameAsJava(jsontest.test.EnumTest())
    assertJsonIsSameAsJava(jsontest.test.EnumTest(Some(MyEnum.V1)))
  }

  "Enum" should "be serialized the same way as java - non-existent string value" in {
    val parser = JavaJsonParser.ignoringUnknownFields()
    val enumJson = """{"enum": "XOXO"}"""

    javaParse(enumJson, jsontest.Test.EnumTest.newBuilder, parser).toString must be("")
    new Parser().ignoringUnknownFields.fromJsonString[jsontest.test.EnumTest](enumJson) must be(
      jsontest.test.EnumTest(None)
    )
  }

  "Enum" should "be serialized the same way as java - non-existent int value" in {
    val parser = JavaJsonParser.ignoringUnknownFields()
    val enumJson = """{"enum": 10}"""

    javaParse(enumJson, jsontest.Test.EnumTest.newBuilder, parser).toString must be("")
    new Parser().ignoringUnknownFields.fromJsonString[jsontest.test.EnumTest](enumJson) must be(
      jsontest.test.EnumTest(Some(MyEnum.Unrecognized(10)))
    )
  }

}
