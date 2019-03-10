package scalapb.json

import org.scalatest.{FlatSpec, MustMatchers}
import scalapb.json4s.JsonFormat

import foo.foo.TestMessage
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

class NameUtilsSpec extends FlatSpec with MustMatchers {
  val M = TestMessage(myField=Some(45), anotherField=Some(32))
  val JsonStr = """{"myField": 45, "anotherField": 32}"""

  "Descriptor fields" should "have empty JSON name" in {
    TestMessage.scalaDescriptor.fields(0).asProto.jsonName must be (None)
    TestMessage.scalaDescriptor.fields(1).asProto.jsonName must be (None)
  }

  "messages" should "serialize" in {
    JsonFormat.toJson(TestMessage()) must be (parse("{}"))
    JsonFormat.toJson(M) must be (parse(JsonStr))
  }

  "parsing" should "work" in {
    JsonFormat.fromJsonString[TestMessage](JsonStr) must be(M)
  }
}
