package com.trueaccord.scalapb.json

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{EnumValueDescriptor, FieldDescriptor}
import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import org.json4s.JsonAST._
import org.json4s.{Reader, Writer}

case class JsonFormatException(msg: String, cause: Exception) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

object JsonFormat {
  def toJsonString[A](m: GeneratedMessage): String = {
    import org.json4s.jackson.JsonMethods._
    compact(toJson(m))
  }

  def fromJsonString[A <: GeneratedMessage with Message[A]](str: String)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    import org.json4s.jackson.JsonMethods._
    fromJson(parse(str))
  }

  def toJson[A](m: GeneratedMessage): JObject = {
    JObject(
      m.getAllFields
        .map {
          case (fd, v) =>
            fd.getJsonName -> serializeField(fd, v)
        }.toList)
  }

  def fromJson[A <: GeneratedMessage with Message[A]](value: JValue)(
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
      case (JavaType.INT, JInt(x)) => x.intValue
      case (JavaType.INT, JDouble(x)) => x.intValue
      case (JavaType.INT, JDecimal(x)) => x.intValue
      case (JavaType.INT, JNull) => 0
      case (JavaType.LONG, JLong(x)) => x.toLong
      case (JavaType.LONG, JDecimal(x)) => x.longValue()
      case (JavaType.LONG, JInt(x)) => x.toLong
      case (JavaType.LONG, JNull) => 0L
      case (JavaType.DOUBLE, JDouble(x)) => x
      case (JavaType.DOUBLE, JInt(x)) => x.toDouble
      case (JavaType.DOUBLE, JDecimal(x)) => x.toDouble
      case (JavaType.DOUBLE, JNull) => 0.toDouble
      case (JavaType.FLOAT, JDouble(x)) => x.toFloat
      case (JavaType.FLOAT, JInt(x)) => x.toFloat
      case (JavaType.FLOAT, JDecimal(x)) => x.toFloat
      case (JavaType.FLOAT, JNull) => 0.toFloat
      case (JavaType.BOOLEAN, JBool(b)) => b
      case (JavaType.BOOLEAN, JNull) => false
      case (JavaType.STRING, JString(s)) => s
      case (JavaType.STRING, JNull) => ""
      case (JavaType.BYTE_STRING, JString(s)) =>
        ByteString.copyFrom(Base64Variants.getDefaultVariant.decode(s))
      case (JavaType.BYTE_STRING, JNull) => ByteString.EMPTY
      case _ => throw new JsonFormatException(
        s"Unexpected value ($value) for field ${fd.getJsonName} of ${fd.getContainingType.getName}")
    }

    value match {
      case JObject(fields) =>
        val values: Map[String, JValue] = fields.map(k => k._1 -> k._2).toMap

        val valueMap: Map[FieldDescriptor, Any] = (for {
          fd <- cmp.descriptor.getFields.asScala
          jsValue <- values.get(fd.getJsonName)
        } yield (fd, parseValue(fd, jsValue))).toMap

        cmp.fromFieldsMap(valueMap)
      case _ =>
        throw new JsonFormatException(s"Expected an object, found ${value}")
    }
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

  implicit def protoToReader[T <: GeneratedMessage with Message[T] : GeneratedMessageCompanion]: Reader[T] =
    new Reader[T] {
      def read(value: JValue): T = fromJson(value)
    }

  implicit def protoToWriter[T <: GeneratedMessage with Message[T]]: Writer[T] = new Writer[T] {
    def write(obj: T): JValue = toJson(obj)
  }
}
