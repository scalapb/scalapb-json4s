package scalapb.json4s

import com.google.protobuf.any.{Any => PBAny}
import com.google.protobuf.struct.Value
import com.google.protobuf.wrappers.{DoubleValue, UInt64Value}
import jsontest.test.MyTest
import jsontest.test3.MyTest3.MyEnum3
import scalapb.descriptors.{PInt, PLong}
import org.json4s.JsonAST._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class ParserLimitsSpec extends AnyFlatSpec with Matchers {
  private val parser = new Parser()

  "Nesting limits" should "bound raw JSON and prebuilt trees before recursive conversion" in {
    def array(depth: Int): JValue =
      (1 to depth).foldLeft[JValue](JNull)((v, _) => JArray(List(v)))
    parser.fromJson[Value](array(100))
    parser.fromJsonString[Value]("[" * 100 + "null" + "]" * 100)
    intercept[JsonFormatException](parser.fromJson[Value](array(101)))
    intercept[JsonFormatException](
      parser.fromJsonString[Value]("[" * 101 + "null" + "]" * 101)
    )
    intercept[JsonFormatException](parser.fromJson[Value](array(10000)))
    parser.withNestingLimit(2).fromJson[Value](array(2))
    intercept[JsonFormatException](
      parser.withNestingLimit(2).fromJson[Value](array(3))
    )
    parser.withNestingLimit(0).fromJson[Value](JNull)
    intercept[JsonFormatException](
      parser.withNestingLimit(0).fromJson[Value](JObject())
    )
    intercept[IllegalArgumentException](parser.withNestingLimit(-1))
  }

  it should "cover ignored fields, ordinary messages, and repeated siblings" in {
    intercept[JsonFormatException] {
      parser.ignoringUnknownFields
        .withNestingLimit(2)
        .fromJsonString[MyTest]("""{"ignored":{"nested":{}}}""")
    }
    parser.withNestingLimit(2).fromJsonString[MyTest]("""{"optMessage":{}}""")
    intercept[JsonFormatException] {
      parser
        .withNestingLimit(2)
        .fromJsonString[MyTest]("""{"optMessage":{"optMessage":{}}}""")
    }
    parser
      .withNestingLimit(3)
      .fromJsonString[MyTest](
        "{\"repMessage\":[" + List.fill(200)("{}").mkString(",") + "]}"
      )
  }

  it should "include Any-of-Any value envelopes without resetting depth" in {
    def any(depth: Int): JValue =
      (1 to depth).foldLeft[JValue](
        JObject(
          "@type" -> JString("type.googleapis.com/google.protobuf.Int32Value"),
          "value" -> JInt(1)
        )
      ) { (v, _) =>
        JObject(
          "@type" -> JString("type.googleapis.com/google.protobuf.Any"),
          "value" -> v
        )
      }
    parser.fromJson[PBAny](any(99))
    intercept[JsonFormatException](parser.fromJson[PBAny](any(100)))
    val p = parser.withNestingLimit(4)
    p.fromJson[PBAny](any(3))
    intercept[JsonFormatException](p.fromJson[PBAny](any(4)))
    // The same immutable parser remains usable after a failure.
    p.fromJson[PBAny](any(3))
  }

  "Numeric limits" should "reject long raw and quoted numbers while preserving long strings" in {
    val oversized = "1" * 1025
    intercept[JsonFormatException](
      parser.fromJsonString[DoubleValue](oversized)
    )
    intercept[JsonFormatException](
      parser.fromJsonString[DoubleValue]("\"" + oversized + "\"")
    )
    intercept[JsonFormatException](
      parser.fromJson[DoubleValue](JInt(BigInt(oversized)))
    )
    parser
      .fromJsonString[MyTest]("{\"hello\":\"" + oversized + "\"}")
      .hello must be(Some(oversized))
    JsonFormat.parseDouble("0." + "0" * 1021 + "1").value must be(0.0)
    intercept[JsonFormatException](
      JsonFormat.parseDouble("0." + "0" * 1022 + "1")
    )
  }

  it should "reject huge unsigned exponents before expanding them" in {
    for (value <- Seq("1e100000000", "-1e100000000", "1e-100000000")) {
      intercept[JsonFormatException](JsonFormat.parseUint32(value))
      intercept[JsonFormatException](JsonFormat.parseUint64(value))
    }
    JsonFormat.parseUint32("4294967295.0") must be(PInt(-1))
    JsonFormat.parseUint64("18446744073709551615.0") must be(PLong(-1L))
    JsonFormat.parseUint64("0e100000000") must be(PLong(0L))
    parser.fromJsonString[UInt64Value]("\"1e2\"").value must be(100L)
    intercept[JsonFormatException](
      JsonFormat.parseUint64("18446744073709551616")
    )
  }

  it should "reject enum integers that would truncate to a recognized value" in {
    val descriptor = MyEnum3.scalaDescriptor
    intercept[JsonFormatException](
      parser.defaultEnumParser(descriptor, JInt(BigInt(1) << 32))
    )
    intercept[JsonFormatException](
      parser.defaultEnumParser(descriptor, JString("1" * 1025))
    )
  }
}
