package scalapb.json4s

import jsontest.oldRepeatables._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class OldRepeatablesSpec extends AnyFlatSpec with Matchers {
  "JsonFormat" should "serialize some old generated message with repeatables" in {
    val oldRepeatablesTest = OldRepeatablesTest(strings = Seq.empty)
    val json = JsonFormat.toJson(oldRepeatablesTest)
    JsonFormat.fromJson[OldRepeatablesTest](json) mustBe oldRepeatablesTest
  }
}
