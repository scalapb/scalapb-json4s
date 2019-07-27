package scalapb.json4s

import com.google.protobuf.ByteString
import jsontest.test3._
import org.json4s.JsonAST.{JBool, JDecimal, JDouble, JString}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{JInt, JValue}
import org.scalatest.{FlatSpec, MustMatchers}


class PrimitiveWrappersSpec extends FlatSpec with MustMatchers {
  "Empty object" should "give empty json for Wrapper" in {
    JsonFormat.toJson(Wrapper()) must be (render(Map.empty[String, JValue]))
  }

  "primitive values" should "serialize properly" in {
    JsonFormat.toJson(Wrapper(wBool = Some(false)))   must be (render(Map("wBool" -> JBool(false))))
    JsonFormat.toJson(Wrapper(wBool = Some(true)))    must be (render(Map("wBool" -> JBool(true))))
    JsonFormat.toJson(Wrapper(wDouble = Some(3.1)))   must be (render(Map("wDouble" -> JDouble(3.1))))
    JsonFormat.toJson(Wrapper(wFloat = Some(3.0f)))   must be (render(Map("wFloat" -> JDecimal(3.0))))
    JsonFormat.toJson(Wrapper(wInt32 = Some(35544)))  must be (render(Map("wInt32" -> JInt(35544))))
    JsonFormat.toJson(Wrapper(wInt32 = Some(0)))      must be (render(Map("wInt32" -> JInt(0))))
    JsonFormat.toJson(Wrapper(wInt64 = Some(125)))    must be (render(Map("wInt64" -> JString("125"))))
    JsonFormat.toJson(Wrapper(wUint32 = Some(125)))   must be (render(Map("wUint32" -> JInt(125))))
    JsonFormat.toJson(Wrapper(wUint64 = Some(125)))   must be (render(Map("wUint64" -> JString("125"))))
    JsonFormat.toJson(Wrapper(wString = Some("bar"))) must be (render(Map("wString" -> JString("bar"))))
    JsonFormat.toJson(Wrapper(wString = Some("")))    must be (render(Map("wString" -> JString(""))))
    JsonFormat.toJson(Wrapper(wBytes = Some(ByteString.copyFrom(Array[Byte](3,5,4))))) must be (
      render(Map("wBytes" -> JString("AwUE"))))
    JsonFormat.toJson(Wrapper(wBytes = Some(ByteString.EMPTY))) must be (
      render(Map("wBytes" -> JString(""))))
  }

  "primitive values" should "parse properly" in {
    JsonFormat.fromJson[Wrapper](render(Map("wBool" -> JBool(false))))    must be (Wrapper(wBool = Some(false)))
    JsonFormat.fromJson[Wrapper](render(Map("wBool" -> JBool(true))))     must be (Wrapper(wBool = Some(true)))
    JsonFormat.fromJson[Wrapper](render(Map("wDouble" -> JDouble(3.1))))  must be (Wrapper(wDouble = Some(3.1)))
    JsonFormat.fromJson[Wrapper](render(Map("wDouble" -> JString("3.1"))))  must be (Wrapper(wDouble = Some(3.1)))
    JsonFormat.fromJson[Wrapper](render(Map("wFloat" -> JDecimal(3.0))))   must be (Wrapper(wFloat = Some(3.0f)))
    JsonFormat.fromJson[Wrapper](render(Map("wFloat" -> JDouble(3.0))))   must be (Wrapper(wFloat = Some(3.0f)))
    JsonFormat.fromJson[Wrapper](render(Map("wInt32" -> JInt(35544))))    must be (Wrapper(wInt32 = Some(35544)))
    JsonFormat.fromJson[Wrapper](render(Map("wInt32" -> JInt(0))))        must be (Wrapper(wInt32 = Some(0)))
    JsonFormat.fromJson[Wrapper](render(Map("wInt64" -> JString("125")))) must be (Wrapper(wInt64 = Some(125)))
    JsonFormat.fromJson[Wrapper](render(Map("wUint32" -> JInt(125))))     must be (Wrapper(wUint32 = Some(125)))
    JsonFormat.fromJson[Wrapper](render(Map("wUint64" -> JString("125"))))must be (Wrapper(wUint64 = Some(125)))
    JsonFormat.fromJson[Wrapper](render(Map("wString" -> JString("bar"))))must be (Wrapper(wString = Some("bar")))
    JsonFormat.fromJson[Wrapper](render(Map("wString" -> JString(""))))   must be (Wrapper(wString = Some("")))
    JsonFormat.fromJson[Wrapper](render(Map("wBytes" -> JString("AwUE"))))   must be (Wrapper(wBytes = Some(ByteString.copyFrom(Array[Byte](3,5,4)))))
    JsonFormat.fromJson[Wrapper](render(Map("wBytes" -> JString(""))))   must be (Wrapper(wBytes = Some(ByteString.EMPTY)))
  }

}
