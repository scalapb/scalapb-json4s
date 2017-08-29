package com.trueaccord.scalapb.json

import org.json4s.{JDouble, JValue}
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import org.scalatest.{FlatSpec, MustMatchers, OptionValues}
import jsontest.test._
import jsontest.test3._
import com.google.protobuf.util.{JsonFormat => JavaJsonFormat}
import com.google.protobuf.any.{Any => PBAny}

class JsonFormatSpec extends FlatSpec with MustMatchers with OptionValues {
  val TestProto = MyTest().update(
    _.hello := "Foo",
    _.foobar := 37,
    _.primitiveSequence := Seq("a", "b", "c"),
    _.repMessage := Seq(MyTest(), MyTest(hello = Some("h11"))),
    _.optMessage := MyTest().update(_.foobar := 39),
    _.stringToInt32 := Map("foo" -> 14, "bar" -> 19),
    _.intToMytest := Map(14 -> MyTest(), 35 -> MyTest(hello = Some("boo"))),
    _.repEnum := Seq(MyEnum.V1, MyEnum.V2, MyEnum.UNKNOWN),
    _.optEnum := MyEnum.V2,
    _.intToEnum := Map(32 -> MyEnum.V1, 35 -> MyEnum.V2),
    _.stringToBool := Map("ff" -> false, "tt" -> true),
    _.boolToString := Map(false -> "ff", true -> "tt"),
    _.optBool := false
  )

  val TestJson =
    """{
      |  "hello": "Foo",
      |  "foobar": 37,
      |  "primitiveSequence": ["a", "b", "c"],
      |  "repMessage": [{}, {"hello": "h11"}],
      |  "optMessage": {"foobar": 39},
      |  "stringToInt32": {"foo": 14, "bar": 19},
      |  "intToMytest": {"14": {}, "35": {"hello": "boo"}},
      |  "repEnum": ["V1", "V2", "UNKNOWN"],
      |  "optEnum": "V2",
      |  "intToEnum": {"32": "V1", "35": "V2"},
      |  "stringToBool": {"ff": false, "tt": true},
      |  "boolToString": {"false": "ff", "true": "tt"},
      |  "optBool": false
      |}
      |""".stripMargin

  val TestJsonWithType =
    """{
      |  "@type": "type.googleapis.com/jsontest.MyTest",
      |  "hello": "Foo",
      |  "foobar": 37,
      |  "primitiveSequence": ["a", "b", "c"],
      |  "repMessage": [{}, {"hello": "h11"}],
      |  "optMessage": {"foobar": 39},
      |  "stringToInt32": {"foo": 14, "bar": 19},
      |  "intToMytest": {"14": {}, "35": {"hello": "boo"}},
      |  "repEnum": ["V1", "V2", "UNKNOWN"],
      |  "optEnum": "V2",
      |  "intToEnum": {"32": "V1", "35": "V2"},
      |  "stringToBool": {"ff": false, "tt": true},
      |  "boolToString": {"false": "ff", "true": "tt"},
      |  "optBool": false
      |}
      |""".stripMargin

  val DefaultTestJson =
    """{
      |  "hello": "",
      |  "foobar": 0,
      |  "bazinga": 0,
      |  "primitiveSequence": [],
      |  "repMessage": [],
      |  "stringToInt32": {},
      |  "intToMytest": {},
      |  "repEnum": [],
      |  "optEnum": "UNKNOWN",
      |  "intToEnum": {},
      |  "boolToString": {},
      |  "stringToBool": {},
      |  "optBool": false
      |}""".stripMargin

  val PreservedTestJson =
    """{
      |  "hello": "Foo",
      |  "foobar": 37,
      |  "primitive_sequence": ["a", "b", "c"],
      |  "rep_message": [{}, {"hello": "h11"}],
      |  "opt_message": {"foobar": 39},
      |  "string_to_int32": {"foo": 14, "bar": 19},
      |  "int_to_mytest": {"14": {}, "35": {"hello": "boo"}},
      |  "rep_enum": ["V1", "V2", "UNKNOWN"],
      |  "opt_enum": "V2",
      |  "int_to_enum": {"32": "V1", "35": "V2"},
      |  "string_to_bool": {"ff": false, "tt": true},
      |  "bool_to_string": {"false": "ff", "true": "tt"},
      |  "opt_bool": false
      |}
      |""".stripMargin

  "Empty object" should "give empty json" in {
    JsonFormat.toJson(MyTest()) must be (render(Map.empty[String, JValue]))
  }

  "Empty object" should "give empty json for MyTest3" in {
    JsonFormat.toJson(MyTest3()) must be (render(Map.empty[String, JValue]))
  }

  "Zero maps" should "give correct json" in {
    JsonFormat.toJson(MyTest(
      stringToInt32 = Map("" -> 17),
      intToMytest = Map(0 -> MyTest()),
      fixed64ToBytes = Map(0L -> com.google.protobuf.ByteString.copyFromUtf8("foobar")))) must be (
        parse("""|{
                 |  "stringToInt32": {"": 17},
                 |  "intToMytest": {"0": {}},
                 |  "fixed64ToBytes": {"0": "Zm9vYmFy"}
                 |}""".stripMargin))
  }

  "Zero maps" should "give correct json for MyTest3" in {
    JsonFormat.toJson(MyTest3(
      stringToInt32 = Map("" -> 17),
      intToMytest = Map(0 -> MyTest()),
      fixed64ToBytes = Map(0L -> com.google.protobuf.ByteString.copyFromUtf8("foobar")))) must be (
        parse("""|{
                 |  "stringToInt32": {"": 17},
                 |  "intToMytest": {"0": {}},
                 |  "fixed64ToBytes": {"0": "Zm9vYmFy"}
                 |}""".stripMargin))
  }

  "Set treat" should "give correct json" in {
    JsonFormat.toJson(MyTest(trickOrTreat = MyTest.TrickOrTreat.Treat(MyTest()))) must be (
        parse("""{"treat": {}}"""))
  }

  "Parse treat" should "give correct proto with proto2" in {
    JsonFormat.fromJsonString[MyTest]("""{"treat": {"hello": "x"}}""") must be(
      MyTest(trickOrTreat=MyTest.TrickOrTreat.Treat(MyTest(hello=Some("x")))))
    JsonFormat.fromJsonString[MyTest]("""{"treat": {}}""") must be(
      MyTest(trickOrTreat=MyTest.TrickOrTreat.Treat(MyTest())))
  }

  "Parse treat" should "give correct proto with proto3" in {
    JsonFormat.fromJsonString[MyTest3]("""{"treat": {"s": "x"}}""") must be(
      MyTest3(trickOrTreat=MyTest3.TrickOrTreat.Treat(MyTest3(s="x"))))
    JsonFormat.fromJsonString[MyTest3]("""{"treat": {}}""") must be(
      MyTest3(trickOrTreat=MyTest3.TrickOrTreat.Treat(MyTest3())))
  }

  "parsing one offs" should "work correctly for issue 315" in {
    JsonFormat.fromJsonString[jsontest.issue315.Msg]("""
    {
          "baz" : "1",
          "foo" : {
            "cols" : "1"
          }
    }""") must be(
      jsontest.issue315.Msg(baz="1", someUnion=jsontest.issue315.Msg.SomeUnion.Foo(jsontest.issue315.Foo(cols="1"))))
  }

  "TestProto" should "be TestJson when converted to Proto" in {
    println("---------------------------")
    JsonFormat.toJson(TestProto) must be (parse(TestJson))
  }

  "TestJson" should "be TestProto when parsed from json" in {
    JsonFormat.fromJsonString[MyTest](TestJson) must be (TestProto)
  }

  "fromJsonString" should "read json produced by Java" in {
    val javaJson = JavaJsonFormat.printer().print(MyTest.toJavaProto(TestProto))
    JsonFormat.fromJsonString[MyTest](javaJson) must be (TestProto)
  }

  "Java parser" should "read json strings produced by us" in {
    val b = jsontest.Test.MyTest.newBuilder
    val javaTestProto = JavaJsonFormat.parser().merge(JsonFormat.toJsonString(TestProto), b)
    TestProto must be(MyTest.fromJavaProto(b.build))
  }

  "Empty object" should "give full json if including default values" in {
    new Printer(includingDefaultValueFields = true).toJson(MyTest()) must be(
      parse(
        """{
          |  "hello": "",
          |  "foobar": 0,
          |  "bazinga": "0",
          |  "primitiveSequence": [],
          |  "repMessage": [],
          |  "stringToInt32": {},
          |  "intToMytest": {},
          |  "repEnum": [],
          |  "optEnum": "UNKNOWN",
          |  "intToEnum": {},
          |  "boolToString": {},
          |  "stringToBool": {},
          |  "optBs": "",
          |  "optBool": false,
          |  "trick": 0,
          |  "fixed64ToBytes": {}
          |}""".stripMargin)
    )
  }

  "Empty object" should "with preserve field names should work" in {
    new Printer(includingDefaultValueFields = true, preservingProtoFieldNames = true).toJson(MyTest()) must be(
      parse(
        """{
          |  "hello": "",
          |  "foobar": 0,
          |  "bazinga": "0",
          |  "primitive_sequence": [],
          |  "rep_message": [],
          |  "string_to_int32": {},
          |  "int_to_mytest": {},
          |  "rep_enum": [],
          |  "opt_enum": "UNKNOWN",
          |  "int_to_enum": {},
          |  "bool_to_string": {},
          |  "string_to_bool": {},
          |  "opt_bs": "",
          |  "opt_bool": false,
          |  "trick": 0,
          |  "fixed64_to_bytes": {}
          |}""".stripMargin)
    )
  }

  "TestProto" should "format int64 as JSON string" in {
    new Printer().print(MyTest(bazinga = Some(642))) must be("""{"bazinga":"642"}""")
  }

  "TestProto" should "format int64 as JSON number" in {
    new Printer(formattingLongAsNumber = true).print(MyTest(bazinga = Some(642))) must be("""{"bazinga":642}""")
  }

  "TestProto" should "parse a number formatted as JSON string" in {
    new Parser().fromJsonString[MyTest]("""{"bazinga":642}""") must be(MyTest(bazinga = Some(642)))
  }

  "TestProto" should "parse an enum formatted as number" in {
    new Parser().fromJsonString[MyTest]("""{"optEnum":1}""") must be(MyTest(optEnum = Some(MyEnum.V1)))
    new Parser().fromJsonString[MyTest]("""{"optEnum":2}""") must be(MyTest(optEnum = Some(MyEnum.V2)))
  }

  "PreservedTestJson" should "be TestProto when parsed from json" in {
    new Parser(preservingProtoFieldNames = true).fromJsonString[MyTest](PreservedTestJson) must be (TestProto)
  }

  "DoubleFloatProto" should "parse NaNs" in {
    val i = s"""{
      "d": "NaN",
      "f": "NaN"
    }"""
    val out = JsonFormat.fromJsonString[DoubleFloat](i)
    out.d.value.isNaN must be (true)
    out.f.value.isNaN must be (true)
    (JsonFormat.toJson(out) \ "d").asInstanceOf[JDouble].num.isNaN must be(true)
    (JsonFormat.toJson(out) \ "f").asInstanceOf[JDouble].num.isNaN must be(true)
  }

  "DoubleFloatProto" should "parse Infinity" in {
    val i = s"""{
      "d": "Infinity",
      "f": "Infinity"
    }"""
    val out = JsonFormat.fromJsonString[DoubleFloat](i)
    out.d.value.isPosInfinity must be (true)
    out.f.value.isPosInfinity must be (true)
    (JsonFormat.toJson(out) \ "d") must be (JDouble(Double.PositiveInfinity))
    (JsonFormat.toJson(out) \ "f") must be (JDouble(Double.PositiveInfinity))
  }

  "DoubleFloatProto" should "parse -Infinity" in {
    val i = s"""{
      "d": "-Infinity",
      "f": "-Infinity"
    }"""
    val out = JsonFormat.fromJsonString[DoubleFloat](i)
    out.d.value.isNegInfinity must be (true)
    out.f.value.isNegInfinity must be (true)
    (JsonFormat.toJson(out) \ "d") must be (JDouble(Double.NegativeInfinity))
    (JsonFormat.toJson(out) \ "f") must be (JDouble(Double.NegativeInfinity))
  }

  val anyEnabledFormatRegistry = JsonFormat.DefaultRegistry.registerCompanion(MyTest)
  val anyEnabledParser = new Parser(formatRegistry = anyEnabledFormatRegistry)
  val anyEnabledPrinter = new Printer(formatRegistry = anyEnabledFormatRegistry)

  "TestProto packed as any" should "give TestJsonWithType after JSON serialization" in {
    val any = PBAny.pack(TestProto)

    anyEnabledPrinter.toJson(any) must be (parse(TestJsonWithType))
  }

  "TestJsonWithType" should "be TestProto packed as any when parsed from JSON" in {
    val out = anyEnabledParser.fromJson[PBAny](TestJsonWithType)
    out must be (PBAny.pack(TestProto))
  }

  "Any" should "parse JSON produced by Java for a packed TestProto" in {
    val any = com.google.protobuf.Any.pack(MyTest.toJavaProto(TestProto))
    val in = JavaJsonFormat.printer().print(any)

    JsonFormat.fromJsonString[PBAny](in) must be (PBAny.pack(TestProto))
  }
}
