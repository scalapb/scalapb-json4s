package scalapb.json4s

import com.google.protobuf.ByteString
import com.google.protobuf.struct.NullValue
import com.google.protobuf.wrappers.{BoolValue, BytesValue}
import jsontest.oneof.OneOf
import jsontest.test.MyEnum
import jsontest.test.MyTest
import org.json4s.JsonAST._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class ParsingConformanceSpec extends AnyFlatSpec with Matchers {
  "Bytes parsing" should "accept both Base64 alphabets with and without padding" in {
    val expected = ByteString.copyFrom(Array(0xfb.toByte, 0xff.toByte))
    for (encoded <- Seq("+/8=", "+/8", "-_8=", "-_8")) {
      JsonFormat
        .fromJsonString[BytesValue]("\"" + encoded + "\"")
        .value must be(expected)
      val javaValue = com.google.protobuf.BytesValue.newBuilder()
      com.google.protobuf.util.JsonFormat
        .parser()
        .merge("\"" + encoded + "\"", javaValue)
      javaValue.getValue must be(expected)
    }
    intercept[JsonFormatException](
      JsonFormat.fromJsonString[BytesValue]("\"%%%\"")
    )
  }

  "NullValue parsing" should "preserve oneof presence and detect overlap" in {
    val parsed = JsonFormat.fromJsonString[OneOf]("""{"nullValue":null}""")
    parsed.field must be(OneOf.Field.NullValue(NullValue.NULL_VALUE))
    JsonFormat.toJson(parsed) must be(JObject("nullValue" -> JNull))
    val javaValue = jsontest.Oneof.OneOf.newBuilder()
    com.google.protobuf.util.JsonFormat
      .parser()
      .merge("""{"nullValue":null}""", javaValue)
    OneOf.fromJavaProto(javaValue.build()) must be(parsed)
    intercept[JsonFormatException] {
      JsonFormat.fromJsonString[OneOf]("""{"primitive":"x","nullValue":null}""")
    }
  }

  "Quoted boolean parsing" should "already accept the recommended conformance forms" in {
    JsonFormat.fromJsonString[BoolValue]("\"true\"").value must be(true)
    JsonFormat.fromJsonString[BoolValue]("\"false\"").value must be(false)
  }

  "Enum formatters" should "support application-defined JSON names independently of Edition 2026" in {
    val custom = "a\"b"
    val registry = JsonFormat.DefaultRegistry.registerEnumFormatter[MyEnum](
      (_, value) => JString(if (value.number == 1) custom else value.name),
      (parser, value) =>
        if (value == JString(custom))
          MyEnum.scalaDescriptor.findValueByNumber(1)
        else parser.defaultEnumParser(MyEnum.scalaDescriptor, value)
    )
    val parser = new Parser().withFormatRegistry(registry)
    val printer = new Printer().withFormatRegistry(registry)
    val message = MyTest(optEnum = Some(MyEnum.V1))
    printer.toJson(message) must be(JObject("optEnum" -> JString(custom)))
    parser.fromJson[MyTest](printer.toJson(message)) must be(message)
    parser.fromJson[MyTest](JObject("optEnum" -> JString("V1"))) must be(
      message
    )
  }
}
