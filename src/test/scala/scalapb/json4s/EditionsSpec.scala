package scalapb.json4s

import jsontest.editions.EditionJson
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.json4s.JsonAST._

class EditionsSpec extends AnyFlatSpec with Matchers {
  "Edition 2023 JSON" should "use resolved presence instead of file syntax" in {
    JsonFormat.toJson(EditionJson()) must be(JObject(Nil))
    JsonFormat.toJson(EditionJson(explicitValue = Some(0))) must be(
      JObject(List("explicitValue" -> JInt(0)))
    )
  }

  it should "accept unknown open enum numbers and reject closed enum numbers" in {
    JsonFormat.fromJsonString[EditionJson]("""{"open":123}""").open must be(
      Some(EditionJson.Open.Unrecognized(123))
    )
    intercept[JsonFormatException] {
      JsonFormat.fromJsonString[EditionJson]("""{"closed":123}""")
    }
  }

  it should "round trip DELIMITED messages as JSON objects" in {
    val value =
      EditionJson(payload = Some(EditionJson.Payload(value = Some(7))))
    val json = JsonFormat.toJson(value)
    json must be(JObject(List("payload" -> JObject(List("value" -> JInt(7))))))
    JsonFormat.fromJson[EditionJson](json) must be(value)
  }
}
