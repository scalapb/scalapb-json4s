package scalapb.json4s

import jsontest.custom_collection.GoogleWrapped
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class CustomCollectionSpec extends AnyFlatSpec with Matchers {
  "JsonFormat" should "serialize/deserialize the custom collection message" in {
    val googleWrapped = GoogleWrapped(strings = collection.immutable.Seq.empty)
    val json = JsonFormat.toJson(googleWrapped)
    JsonFormat.fromJson[GoogleWrapped](json) mustBe googleWrapped
  }
}
