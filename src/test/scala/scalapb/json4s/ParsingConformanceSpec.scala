package scalapb.json4s

import com.google.protobuf.ByteString
import com.google.protobuf.struct.NullValue
import com.google.protobuf.wrappers.{BoolValue, BytesValue}
import jsontest.oneof.OneOf
import jsontest.test.MyEnum
import jsontest.test.MyTest
import org.json4s.JsonAST._
import scalapb.descriptors.{PInt, PLong}
import jsontest.test3.MyTest3.MyEnum3
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

  "Bytes compatibility" should "preserve legacy whitespace and reject malformed input" in {
    for (encoded <- Seq("", " Zm9v", "Zm9v\nYmFy", "Zm9v ", "+/8=")) {
      val expected = com.fasterxml.jackson.core.Base64Variants.getDefaultVariant
        .decode(encoded)
      new Parser().fromJson[BytesValue](JString(encoded)).value must be(
        ByteString.copyFrom(expected)
      )
    }
    for (encoded <- Seq("%", "A", "Zg===", "Zg==!")) {
      intercept[JsonFormatException] {
        new Parser().fromJson[BytesValue](JString(encoded))
      }
    }
  }

  "Unsigned parsing" should "check ranges before expanding exponents" in {
    for (value <- Seq("1e100000000", "-1e100000000", "1e-100000000")) {
      intercept[JsonFormatException](JsonFormat.parseUint32(value))
      intercept[JsonFormatException](JsonFormat.parseUint64(value))
    }
    JsonFormat.parseUint32("4294967295.0") must be(PInt(-1))
    JsonFormat.parseUint64("18446744073709551615.0") must be(PLong(-1L))
    JsonFormat.parseUint64("0e100000000") must be(PLong(0L))
    JsonFormat.parseUint32("1e2") must be(PInt(100))
    JsonFormat.parseUint64("1e2") must be(PLong(100L))
    intercept[JsonFormatException](JsonFormat.parseUint32("4294967296"))
    intercept[JsonFormatException](
      JsonFormat.parseUint64("18446744073709551616")
    )
  }

  "Enum parsing" should "reject overflow without truncating recognized values" in {
    val parser = new Parser()
    val descriptor = MyEnum3.scalaDescriptor
    for (value <- Seq(BigInt(1) << 32, -(BigInt(1) << 32))) {
      intercept[JsonFormatException] {
        parser.defaultEnumParser(descriptor, JInt(value))
      }
      parser.ignoringUnknownFields.defaultEnumParser(
        descriptor,
        JInt(value)
      ) must be(
        descriptor.findValueByNumber(0)
      )
    }
  }

  "Maintenance parsing" should "retain unrestricted depth and numeric literal length" in {
    val nested = (1 to 101).foldLeft[JValue](JNull)((v, _) => JArray(List(v)))
    new Parser().fromJson[com.google.protobuf.struct.Value](nested)
    JsonFormat.parseUint64("0" * 1025 + "1") must be(PLong(1L))
  }

}
