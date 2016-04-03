package com.trueaccord.scalapb.json

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{EnumValueDescriptor, FieldDescriptor}
import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import org.json4s.JsonAST._

case class JsonFormatException(msg: String, cause: Exception) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

object JsonFormat {
  def toJson[A](m: GeneratedMessage): JObject = {
    JObject(
      m.getAllFields
        .map {
          case (fd, v) =>
            fd.getJsonName -> serializeField(fd, v)
        }.toList)
  }

  def fromJson[A <: GeneratedMessage with Message[A]](obj: JObject)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    import scala.collection.JavaConverters._

    def parseValue(fd: FieldDescriptor, value: JValue): Any =
      if (fd.isRepeated) {
        value match {
          case JArray(vals) => vals.map(parseSingleValue(fd, _)).toVector
          case _ => throw new JsonFormatException(
            s"Expected an array for repeated field ${fd.getJsonName} of ${fd.getContainingType.getName}")
        }
      } else parseSingleValue(fd, value)

    def parseSingleValue(fd: FieldDescriptor, value: JValue): Any = (fd.getJavaType, value) match {
      case (JavaType.ENUM, JString(s)) => fd.getEnumType.findValueByName(s)
      case (JavaType.MESSAGE, o: JObject) =>
        // The asInstanceOf[] is a lie: we actually have a companion of some other message (not A),
        // but this doesn't matter after erasure.
        fromJson(o)(cmp.messageCompanionForField(fd).asInstanceOf[GeneratedMessageCompanion[A]])
      case (JavaType.INT, JInt(num)) => num.toInt
      case (JavaType.LONG, JLong(num)) => num.toLong
      case (JavaType.LONG, JInt(num)) => num.toLong
      case (JavaType.DOUBLE, JDouble(dbl)) => dbl
      case (JavaType.FLOAT, JDouble(dbl)) => dbl.toFloat
      case (JavaType.BOOLEAN, JBool(b)) => b
      case (JavaType.STRING, JString(s)) => s
      case (JavaType.BYTE_STRING, JString(s)) =>
        ByteString.copyFrom(Base64Variants.getDefaultVariant.decode(s))
      case _ => throw new JsonFormatException(
        s"Unexpected value ($value) for field ${fd.getJsonName} of ${fd.getContainingType.getName}")
    }

    val values: Map[String, JValue] = obj.obj.map(k => k._1 -> k._2).toMap

    val valueMap: Map[FieldDescriptor, Any] = (for {
      fd <- cmp.descriptor.getFields.asScala
      jsValue <- values.get(fd.getJsonName)
    } yield (fd, parseValue(fd, jsValue))).toMap

    cmp.fromFieldsMap(valueMap)
  }

  @inline
  private def serializeField(fd: FieldDescriptor, value: Any): JValue = {
    if (fd.isRepeated) {
      JArray(value.asInstanceOf[Seq[Any]].map(serializeSingleValue(fd, _)).toList)
    }
    else serializeSingleValue(fd, value)
  }

  @inline
  private def serializeSingleValue(fd: FieldDescriptor, value: Any): JValue = fd.getJavaType match {
    case JavaType.ENUM => JString(value.asInstanceOf[EnumValueDescriptor].getName)
    case JavaType.MESSAGE => toJson(value.asInstanceOf[GeneratedMessage])
    case JavaType.INT => JInt(value.asInstanceOf[Int])
    case JavaType.LONG => JLong(value.asInstanceOf[Long])
    case JavaType.DOUBLE => JDouble(value.asInstanceOf[Double])
    case JavaType.FLOAT => JDouble(value.asInstanceOf[Float])
    case JavaType.BOOLEAN => JBool(value.asInstanceOf[Boolean])
    case JavaType.STRING => JString(value.asInstanceOf[String])
    case JavaType.BYTE_STRING => JString(
      Base64Variants.getDefaultVariant.encode(value.asInstanceOf[ByteString].toByteArray))
  }
}
