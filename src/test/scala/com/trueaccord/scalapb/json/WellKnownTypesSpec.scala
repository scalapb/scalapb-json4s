package com.trueaccord.scalapb.json

import com.google.protobuf.duration.Duration
import jsontest.test._
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods._
import org.scalatest.{ FlatSpec, MustMatchers }

class WellKnownTypesSpec extends FlatSpec with MustMatchers {

  val durationProto = WellKnownTest(duration = Some(Duration(146, 3455)))

  "Duration serializer" should "do" in {
    WellKnownTypes.writeDuration(Duration(146, 3455)) must be (JString("146.00003455s"))
  }

  "duration" should "serialize correctly " in {
    val durationJson = """{
        |  "duration": "146.000003455s"
        |}""".stripMargin
    JsonFormat.printer.toJson(durationProto) must be(parse(durationJson))
  }
}
