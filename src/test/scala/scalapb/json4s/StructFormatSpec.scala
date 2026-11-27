package scalapb.json4s

import com.google.protobuf.struct._
import jsontest.test3.{StructTest, ValueTest}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class StructFormatSpec extends AnyFlatSpec with Matchers with JavaAssertions {
  "Value JSON serialization" should "reject non-finite numbers like Java" in {
    for (
      number <- Seq(
        Double.NaN,
        Double.PositiveInfinity,
        Double.NegativeInfinity
      )
    ) {
      intercept[JsonFormatException] {
        JsonFormat.toJsonString(Value(Value.Kind.NumberValue(number)))
      }
      intercept[IllegalArgumentException] {
        com.google.protobuf.util.JsonFormat
          .printer()
          .print(
            com.google.protobuf.Value
              .newBuilder()
              .setNumberValue(number)
              .build()
          )
      }
    }
    JsonFormat.toJsonString(Value(Value.Kind.StringValue("NaN"))) must be(
      "\"NaN\""
    )
  }

  "Duration JSON serialization" should "enforce the exclusive positive nanos bound" in {
    for (nanos <- Seq(-1000000000, 1000000000)) {
      intercept[IllegalArgumentException] {
        JsonFormat.toJsonString(
          com.google.protobuf.duration.Duration(nanos = nanos)
        )
      }
      intercept[IllegalArgumentException] {
        com.google.protobuf.util.JsonFormat
          .printer()
          .print(
            com.google.protobuf.Duration.newBuilder().setNanos(nanos).build()
          )
      }
    }
    for (nanos <- Seq(-999999999, 999999999)) {
      val value = com.google.protobuf.duration.Duration(nanos = nanos)
      JsonFormat.fromJsonString[com.google.protobuf.duration.Duration](
        JsonFormat.toJsonString(value)
      ) must be(value)
    }
  }

  val ListValueExample = ListValue(
    values = Seq(
      Value(Value.Kind.NumberValue(-245.0)),
      Value(Value.Kind.BoolValue(true)),
      Value(Value.Kind.StringValue("Boom"))
    )
  )

  val StructExample = Struct(
    fields = Map(
      "f1" -> Value(Value.Kind.StringValue("Boo")),
      "f2" -> Value(Value.Kind.ListValue(ListValueExample)),
      "f3" -> Value(
        Value.Kind.StructValue(
          Struct(fields = Map("f4" -> Value(Value.Kind.StringValue("f5"))))
        )
      )
    )
  )

  val StructExample2 = Struct(
    fields = Map(
      "f1" -> Value(Value.Kind.StringValue("Boo")),
      "f2" -> Value(Value.Kind.StructValue(StructExample)),
      "f3" -> Value(Value.Kind.NullValue(NullValue.NULL_VALUE))
    )
  )

  "Empty value" should "be serialized to null" in {
    JavaJsonPrinter.print(
      com.google.protobuf.Value.newBuilder().build()
    ) must be(
      "null"
    )
    JsonFormat.toJsonString(Value()) must be("null")
  }

  "Value" should "be serialized the same as in Java (and parsed back to original)" in {
    assertJsonIsSameAsJava(Value(kind = Value.Kind.NumberValue(1.0)))
    assertJsonIsSameAsJava(Value(kind = Value.Kind.NumberValue(-25)))
    assertJsonIsSameAsJava(Value(kind = Value.Kind.StringValue("boo")))
    assertJsonIsSameAsJava(Value(kind = Value.Kind.BoolValue(true)))
    assertJsonIsSameAsJava(Value(kind = Value.Kind.BoolValue(false)))
    assertJsonIsSameAsJava(
      Value(
        kind =
          Value.Kind.NullValue(com.google.protobuf.struct.NullValue.NULL_VALUE)
      )
    )
    assertJsonIsSameAsJava(
      Value(kind = Value.Kind.StructValue(value = StructExample))
    )

    assertJsonIsSameAsJava(
      Value(
        kind = Value.Kind.ListValue(
          com.google.protobuf.struct.ListValue(
            values = Seq(
              Value(Value.Kind.NumberValue(-17.0)),
              Value(Value.Kind.StringValue("Boo")),
              Value(Value.Kind.StructValue(StructExample2)),
              Value(Value.Kind.BoolValue(false)),
              Value(Value.Kind.ListValue(ListValueExample))
            )
          )
        )
      )
    )
  }

  "Value" should "accept null" in new DefaultParserContext {
    assertParse(
      """{"v": null}""",
      ValueTest().withV(Value().withNullValue(NullValue.NULL_VALUE))
    )
  }

  "Struct" should "be serialized the same as in Java (and parsed back to original)" in {
    assertJsonIsSameAsJava(Struct())
    assertJsonIsSameAsJava(StructExample)
    assertJsonIsSameAsJava(StructExample2)
  }

  "ListValue" should "be serialized the same as in Java (and parsed back to original)" in {
    assertJsonIsSameAsJava(ListValue())
    assertJsonIsSameAsJava(ListValueExample)
  }

  "NullValue" should "be serialized and parsed from JSON correctly" in new DefaultParserContext {
    assertParse("""{"nv": 0}""", StructTest())
    assertParse("""{"nv": null}""", StructTest())
    assertParse("""{"nv": "NULL_VALUE"}""", StructTest())
    assertParse(
      """{"repNv": [0,0.0,0]}""",
      StructTest(
        repNv =
          Seq(NullValue.NULL_VALUE, NullValue.NULL_VALUE, NullValue.NULL_VALUE)
      )
    )
    assertParse(
      """{"repNv": [null,0,null]}""",
      StructTest(
        repNv =
          Seq(NullValue.NULL_VALUE, NullValue.NULL_VALUE, NullValue.NULL_VALUE)
      )
    )

    assertJsonIsSameAsJava(StructTest())
    assertJsonIsSameAsJava(StructTest(nv = NullValue.NULL_VALUE))
    assertJsonIsSameAsJava(
      StructTest(repNv = Seq(NullValue.NULL_VALUE, NullValue.NULL_VALUE))
    )
  }
}
