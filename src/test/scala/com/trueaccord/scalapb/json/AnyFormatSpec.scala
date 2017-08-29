package com.trueaccord.scalapb.json

import com.google.protobuf.any.{Any => PBAny}
import jsontest.issue315.{Bar, Foo}
import org.scalatest.{FlatSpec, MustMatchers}
import org.json4s.jackson.JsonMethods._

class AnyFormatSpec extends FlatSpec with MustMatchers with JavaAssertions {
  val FooExample = Foo("test")

  val FooJson = parse(s"""{"cols":"test"}""")

  val AnyExample = PBAny.pack(FooExample)

  val AnyJson = parse(s"""{"@type":"type.googleapis.com/jsontest.Foo","cols":"test"}""")

  val BarExample = Bar("field1", "field2")

  val CustomPrefixAny = PBAny.pack(FooExample, "example.com/")

  val CustomPrefixJson = parse(s"""{"@type":"example.com/jsontest.Foo","cols":"test"}""")

  override def registeredCompanions = Seq(Foo)

  // For clarity
  def UnregisteredPrinter = JsonFormat.printer
  def UnregisteredParser = JsonFormat.parser

  "Any" should "fail to serialize if its respective companion is not registered" in {
    an [IllegalStateException] must be thrownBy UnregisteredPrinter.toJson(AnyExample)
  }

  "Any" should "fail to deserialize if its respective companion is not registered" in {
    a [JsonFormatException] must be thrownBy UnregisteredParser.fromJson[PBAny](AnyJson)
  }

  "Any" should "serialize correctly if its respective companion is registered" in {
    ScalaJsonPrinter.toJson(AnyExample) must be(AnyJson)
  }

  "Any" should "fail to serialize with a custom URL prefix if specified" in {
    an [IllegalStateException] must be thrownBy ScalaJsonPrinter.toJson(CustomPrefixAny)
  }

  "Any" should "fail to deserialize for a non-Google-prefixed type URL" in {
    a [JsonFormatException] must be thrownBy ScalaJsonParser.fromJson[PBAny](CustomPrefixJson)
  }

  "Any" should "deserialize correctly if its respective companion is registered" in {
    ScalaJsonParser.fromJson[PBAny](AnyJson) must be(AnyExample)
  }

  "Any" should "be serialized the same as in Java (and parsed back to original)" in {
    assertJsonIsSameAsJava(AnyExample)
  }
}
