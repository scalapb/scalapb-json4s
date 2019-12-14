package scalapb.json4s

import com.google.protobuf.{InvalidProtocolBufferException, Message}
import jsontest.test.{EnumTest, MyEnum}
import jsontest.test3.EnumTest3
import jsontest.test3.MyTest3.MyEnum3
import org.scalatest.Assertion
import scalapb.GeneratedMessageCompanion
import scalapb.JavaProtoSupport
import com.google.protobuf.util.JsonFormat.{Parser => JavaParser}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class EnumFormatSpec extends AnyFlatSpec with Matchers with JavaAssertions {
  // not ignoring unknown fields:

  "default parser" should "match Java behavior for string enums" in new DefaultParserContext {
    assertFails("""{"enum":"ZAZA"}""", EnumTest)
    assertFails("""{"enum":"ZAZA"}""", EnumTest3)
    assertFails("""{"enum":""}""", EnumTest)
    assertFails("""{"enum":""}""", EnumTest3)
    assertParse("""{"enum":"V1"}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":"V1"}""", EnumTest3(MyEnum3.V1))
    assertParse("""{"enum":"0"}""", EnumTest(Some(MyEnum.UNKNOWN)))
    assertParse("""{"enum":"0"}""", EnumTest3())
    assertParse("""{"enum":"1.0"}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":"1.0"}""", EnumTest3(MyEnum3.V1))
    assertFails("""{"enum":"1.4"}""", EnumTest)
    assertFails("""{"enum":"1.4"}""", EnumTest3)
    assertFails("""{"enum":"10"}""", EnumTest)
    assertParse("""{"enum":"10"}""", EnumTest3(MyEnum3.Unrecognized(10)))
  }

  "default parser" should "match Java behavior for int enums" in new DefaultParserContext {
    assertFails("""{"enum":10}""", EnumTest)
    assertParse("""{"enum":10}""", EnumTest3(MyEnum3.Unrecognized(10)))
    assertParse("""{"enum":0}""", EnumTest(Some(MyEnum.UNKNOWN)))
    assertParse("""{"enum":0}""", EnumTest3(MyEnum3.UNKNOWN))
    assertParse("""{"enum":0.0}""", EnumTest(Some(MyEnum.UNKNOWN)))
    assertParse("""{"enum":0.0}""", EnumTest3(MyEnum3.UNKNOWN))
    assertFails("""{"enum":0.4}""", EnumTest)
    assertFails("""{"enum":0.4}""", EnumTest3)
    assertParse("""{"enum":1}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":1}""", EnumTest3(MyEnum3.V1))
    assertParse("""{"enum":1.0}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":1.0}""", EnumTest3(MyEnum3.V1))
    assertFails("""{"enum":-1}""", EnumTest)
    assertParse("""{"enum":-1}""", EnumTest3(MyEnum3.Unrecognized(-1)))
  }

  "ignoring unknown fields parser" should "match Java behavior for strings enums" in new IgnoringUnknownParserContext {
    assertParse("""{"enum":"ZAZA"}""", EnumTest())
    assertParse("""{"enum":"ZAZA"}""", EnumTest3())
    assertParse("""{"enum":""}""", EnumTest())
    assertParse("""{"enum":""}""", EnumTest3())
    assertParse("""{"enum":"V1"}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":"V1"}""", EnumTest3(MyEnum3.V1))
    assertParse("""{"enum":"0"}""", EnumTest(Some(MyEnum.UNKNOWN)))
    assertParse("""{"enum":"0"}""", EnumTest3())
    assertParse("""{"enum":"1.0"}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":"1.0"}""", EnumTest3(MyEnum3.V1))
    assertParse("""{"enum":"1.4"}""", EnumTest())
    assertParse("""{"enum":"1.4"}""", EnumTest3())
    assertParse("""{"enum":"10"}""", EnumTest())
    assertParse("""{"enum":"10"}""", EnumTest3(MyEnum3.Unrecognized(10)))
  }

  "ignoring unknown fields parser" should "match Java behavior for int enums" in new IgnoringUnknownParserContext {
    assertParse("""{"enum":10}""", EnumTest())
    assertParse("""{"enum":10}""", EnumTest3(MyEnum3.Unrecognized(10)))
    assertParse("""{"enum":0}""", EnumTest(Some(MyEnum.UNKNOWN)))
    assertParse("""{"enum":0}""", EnumTest3(MyEnum3.UNKNOWN))
    assertParse("""{"enum":0.0}""", EnumTest(Some(MyEnum.UNKNOWN)))
    assertParse("""{"enum":0.0}""", EnumTest3(MyEnum3.UNKNOWN))
    assertParse("""{"enum":0.4}""", EnumTest())
    assertParse("""{"enum":0.4}""", EnumTest())
    assertParse("""{"enum":1}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":1}""", EnumTest3(MyEnum3.V1))
    assertParse("""{"enum":1.0}""", EnumTest(Some(MyEnum.V1)))
    assertParse("""{"enum":1.0}""", EnumTest3(MyEnum3.V1))
    assertParse("""{"enum":-1}""", EnumTest())
    assertParse("""{"enum":-1}""", EnumTest3(MyEnum3.Unrecognized(-1)))
  }

  "Enum" should "be serialized the same way as java" in {
    assertJsonIsSameAsJava(jsontest.test.EnumTest())
    assertJsonIsSameAsJava(jsontest.test.EnumTest(Some(MyEnum.V1)))
  }
}
