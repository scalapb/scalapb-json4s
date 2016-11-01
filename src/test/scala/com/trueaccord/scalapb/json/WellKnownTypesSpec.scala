package com.trueaccord.scalapb.json

import com.google.protobuf.duration.Duration
import jsontest.test._
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods._
import org.scalatest.{ FlatSpec, MustMatchers }

class WellKnownTypesSpec extends FlatSpec with MustMatchers {

  val durationProto = WellKnownTest(duration = Some(Duration(146, 3455)))

  "Duration serializer" should "work" in {
    WellKnownTypes.writeDuration(Duration(146, 3455)) must be (JString("146.000003455s"))
    WellKnownTypes.writeDuration(Duration(146, 3455000)) must be (JString("146.003455s"))
    WellKnownTypes.writeDuration(Duration(146, 345500000)) must be (JString("146.345500s"))
    WellKnownTypes.writeDuration(Duration(146, 345500000)) must be (JString("146.345500s"))
    WellKnownTypes.writeDuration(Duration(146, 345000000)) must be (JString("146.345s"))
    WellKnownTypes.writeDuration(Duration(146, 0)) must be (JString("146s"))
    WellKnownTypes.writeDuration(Duration(-146, 0)) must be (JString("-146s"))
    WellKnownTypes.writeDuration(Duration(-146, -345)) must be (JString("-146.000000345s"))
  }

  "Duration parser" should "work" in {
    WellKnownTypes.parseDuration("146.000003455s")  must be (Duration(146, 3455))
    WellKnownTypes.parseDuration("146.003455s")     must be (Duration(146, 3455000))
    WellKnownTypes.parseDuration("146.345500s")     must be (Duration(146, 345500000))
    WellKnownTypes.parseDuration("146.345500s")     must be (Duration(146, 345500000))
    WellKnownTypes.parseDuration("146.345s")        must be (Duration(146, 345000000))
    WellKnownTypes.parseDuration("146s")            must be (Duration(146, 0))
    WellKnownTypes.parseDuration("-146s")           must be (Duration(-146, 0))
    WellKnownTypes.parseDuration("-146.000000345s") must be (Duration(-146, -345))
  }

  "duration" should "serialize and parse correctly" in {
    val durationJson = """{
        |  "duration": "146.000003455s"
        |}""".stripMargin
    JsonFormat.printer.toJson(durationProto) must be(parse(durationJson))
    JsonFormat.parser.fromJsonString[WellKnownTest](durationJson) must be(durationProto)
  }
}
