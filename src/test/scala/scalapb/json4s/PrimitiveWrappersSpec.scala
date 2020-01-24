package scalapb.json4s

import com.google.protobuf.ByteString
import jsontest.test3._
import org.json4s.JsonAST.{JBool, JDecimal, JDouble, JString}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{JInt, JValue}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class PrimitiveWrappersSpec
    extends AnyFlatSpec
    with Matchers
    with JavaAssertions {
  "Empty object" should "give empty json for Wrapper" in {
    JsonFormat.toJson(Wrapper()) must be(render(Map.empty[String, JValue]))
  }

  "primitive values" should "serialize properly" in {
    JsonFormat.toJson(Wrapper(wBool = Some(false))) must be(
      render(Map("wBool" -> JBool(false)))
    )
    JsonFormat.toJson(Wrapper(wBool = Some(true))) must be(
      render(Map("wBool" -> JBool(true)))
    )
    JsonFormat.toJson(Wrapper(wDouble = Some(3.1))) must be(
      render(Map("wDouble" -> JDouble(3.1)))
    )
    JsonFormat.toJson(Wrapper(wFloat = Some(3.0f))) must be(
      render(Map("wFloat" -> JDecimal(3.0)))
    )
    JsonFormat.toJson(Wrapper(wInt32 = Some(35544))) must be(
      render(Map("wInt32" -> JInt(35544)))
    )
    JsonFormat.toJson(Wrapper(wInt32 = Some(0))) must be(
      render(Map("wInt32" -> JInt(0)))
    )
    JsonFormat.toJson(Wrapper(wInt64 = Some(125))) must be(
      render(Map("wInt64" -> JString("125")))
    )
    JsonFormat.toJson(Wrapper(wUint32 = Some(125))) must be(
      render(Map("wUint32" -> JInt(125)))
    )
    JsonFormat.toJson(Wrapper(wUint64 = Some(125))) must be(
      render(Map("wUint64" -> JString("125")))
    )
    JsonFormat.toJson(Wrapper(wString = Some("bar"))) must be(
      render(Map("wString" -> JString("bar")))
    )
    JsonFormat.toJson(Wrapper(wString = Some(""))) must be(
      render(Map("wString" -> JString("")))
    )
    JsonFormat.toJson(
      Wrapper(wBytes = Some(ByteString.copyFrom(Array[Byte](3, 5, 4))))
    ) must be(render(Map("wBytes" -> JString("AwUE"))))
    JsonFormat.toJson(Wrapper(wBytes = Some(ByteString.EMPTY))) must be(
      render(Map("wBytes" -> JString("")))
    )
  }

  "primitive values" should "serialize with printer config" in {
    new Printer().formattingLongAsNumber.toJson(
      Wrapper(wInt64 = Some(123456))
    ) must be(render(Map("wInt64" -> JInt(123456))))
  }

  "primitive values" should "parse properly" in new DefaultParserContext {
    assertParse(
      compact(render(Map("wBool" -> JBool(false)))),
      Wrapper(wBool = Some(false))
    )
    assertParse(
      compact(render(Map("wBool" -> JBool(true)))),
      Wrapper(wBool = Some(true))
    )
    assertParse(
      compact(render(Map("wDouble" -> JDouble(3.1)))),
      Wrapper(wDouble = Some(3.1))
    )
    assertParse(
      compact(render(Map("wDouble" -> JString("3.1")))),
      Wrapper(wDouble = Some(3.1))
    )
    assertParse(
      compact(render(Map("wFloat" -> JDecimal(3.0)))),
      Wrapper(wFloat = Some(3.0f))
    )
    assertParse(
      compact(render(Map("wFloat" -> JDouble(3.0)))),
      Wrapper(wFloat = Some(3.0f))
    )
    assertParse(
      compact(render(Map("wInt32" -> JInt(35544)))),
      Wrapper(wInt32 = Some(35544))
    )
    assertParse(
      compact(render(Map("wInt32" -> JInt(0)))),
      Wrapper(wInt32 = Some(0))
    )
    assertParse(
      compact(render(Map("wInt64" -> JString("125")))),
      Wrapper(wInt64 = Some(125))
    )
    assertParse(
      compact(render(Map("wUint32" -> JInt(125)))),
      Wrapper(wUint32 = Some(125))
    )
    assertParse(
      compact(render(Map("wUint64" -> JString("125")))),
      Wrapper(wUint64 = Some(125))
    )
    assertParse(
      compact(render(Map("wString" -> JString("bar")))),
      Wrapper(wString = Some("bar"))
    )
    assertParse(
      compact(render(Map("wString" -> JString("")))),
      Wrapper(wString = Some(""))
    )
    assertParse(
      compact(render(Map("wBytes" -> JString("AwUE")))),
      Wrapper(wBytes = Some(ByteString.copyFrom(Array[Byte](3, 5, 4))))
    )
    assertParse(
      compact(render(Map("wBytes" -> JString("")))),
      Wrapper(wBytes = Some(ByteString.EMPTY))
    )
  }
}
