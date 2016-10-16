package com.trueaccord.scalapb.json

import com.google.protobuf.ByteString
import com.google.protobuf.duration.Duration
import com.google.protobuf.timestamp.Timestamp
import org.json4s.JValue
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{FlatSpec, MustMatchers}
import test.test._
import test.test_pb3.TestPB3

class JsonFormatSpec extends FlatSpec with MustMatchers {
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
    _.boolToString := Map(false -> "ff", true -> "tt")
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
      |  "boolToString": {"false": "ff", "true": "tt"}
      |}
      |""".stripMargin

  "Empty object" should "give empty json" in {
    JsonFormat.toJson(MyTest()) must be (render(Map.empty[String, JValue]))
  }

  "TestProto" should "be TestJson when converted to Proto" in {
    JsonFormat.toJson(TestProto) must be (parse(TestJson))
  }

  "TestJson" should "be TestProto when parsed from json" in {
    JsonFormat.fromJsonString[MyTest](TestJson) must be (TestProto)
  }

  val TestPB3Proto = TestPB3(
    name = "Foo",
    intValue = Some(-15),
    longValue = Some(333),
    unsignedLongValue = Some(999),
    floatValue = Some(3.14F),
    doubleValue = Some(5.3333333),
    boolValue = Some(true),
    stringValue = Some("Hello"),
    bytesValue = Some(ByteString.copyFromUtf8("Hello World")),
    birth = Some(Timestamp(seconds = 631152000)),
    blink = Some(Duration(seconds = 3600 * 24, nanos = 150000000))
  )

  val TestPB3Json =
    """{
      |"bytesValue":"SGVsbG8gV29ybGQ=",
      |"name":"Foo",
      |"doubleValue":5.3333333,
      |"birth":"1990-01-01T00:00:00Z",
      |"unsignedLongValue":999,
      |"boolValue":true,
      |"longValue":333,
      |"stringValue":"Hello",
      |"blink":"86400.150000000s",
      |"intValue":-15,
      |"floatValue":3.140000104904175
      |}
      |""".stripMargin.replace("\n", "")

  "TestPB3" should "be TestPB3Json when converted to proto" in {
    JsonFormat.toJsonString(TestPB3Proto) must be (TestPB3Json)
  }

  "TestJsonPB3" should "be TestPB3Proto when parsed from json" in {
    JsonFormat.fromJsonString[TestPB3](TestPB3Json) must be (TestPB3Proto)
  }
}

