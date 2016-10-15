package com.trueaccord.scalapb.json

import java.time.Instant
import java.time.format.DateTimeFormatter

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.{ByteString, InvalidProtocolBufferException}
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{EnumValueDescriptor, FieldDescriptor}
import com.google.protobuf.duration.Duration
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.wrappers._
import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import org.json4s.JsonAST._
import org.json4s.{Reader, Writer}

import scala.language.existentials

case class JsonFormatException(msg: String, cause: Exception) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

object JsonFormat {
  private val BOOL_VALUE_NAME = BoolValue.descriptor.getFullName
  private val INT32_VALUE_NAME = Int32Value.descriptor.getFullName
  private val UINT32_VALUE_NAME =  UInt32Value.descriptor.getFullName
  private val INT64_VALUE_NAME = Int64Value.descriptor.getFullName
  private val UINT64_VALUE_NAME = UInt64Value.descriptor.getFullName
  private val STRING_VALUE_NAME = StringValue.descriptor.getFullName
  private val BYTES_VALUE_NAME = BytesValue.descriptor.getFullName
  private val FLOAT_VALUE_NAME = FloatValue.descriptor.getFullName
  private val DOUBLE_VALUE_NAME = DoubleValue.descriptor.getFullName
  private val TIMESTAMP_VALUE_NAME = Timestamp.descriptor.getFullName
  private val DURATION_VALUE_NAME = Duration.descriptor.getFullName

  def toJsonString[A](m: GeneratedMessage): String = {
    import org.json4s.jackson.JsonMethods._
    compact(toJson(m))
  }

  def fromJsonString[A <: GeneratedMessage with Message[A]](str: String)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    import org.json4s.jackson.JsonMethods._
    fromJson(parse(str))
  }

  def toJson[A](m: GeneratedMessage): JValue = {
    JObject(
      m.getAllFields
        .map {
          case (fd, v) =>
            fd.getJsonName -> serializeField(fd, v)
        }.toList)
  }

  def parseSingleValue(cmp: GeneratedMessageCompanion[_], fd: FieldDescriptor, value: JValue): Any = (fd.getJavaType, value) match {
    case (JavaType.ENUM, JString(s)) => fd.getEnumType.findValueByName(s)
    case (JavaType.MESSAGE, o: JObject) =>
      // The asInstanceOf[] is a lie: we actually have a companion of some other message (not A),
      // but this doesn't matter after erasure.
      fromJson(o)(cmp.messageCompanionForField(fd)
        .asInstanceOf[GeneratedMessageCompanion[T] forSome { type T <: GeneratedMessage with Message[T]}])
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

  def fromJson[A <: GeneratedMessage with Message[A]](value: JValue)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    import scala.collection.JavaConverters._

    def parseValue(fd: FieldDescriptor, value: JValue): Any = {
      if (fd.isMapField) {
        value match {
          case JObject(vals) =>
            val mapEntryCmp = cmp.messageCompanionForField(fd)
            val keyDescriptor = fd.getMessageType.findFieldByNumber(1)
            val valueDescriptor = fd.getMessageType.findFieldByNumber(2)
            vals.map {
              case (key, jValue) =>
                val keyObj = keyDescriptor.getJavaType match {
                  case JavaType.BOOLEAN => java.lang.Boolean.valueOf(key)
                  case JavaType.DOUBLE => java.lang.Double.valueOf(key)
                  case JavaType.FLOAT => java.lang.Float.valueOf(key)
                  case JavaType.INT => java.lang.Integer.valueOf(key)
                  case JavaType.LONG => java.lang.Long.valueOf(key)
                  case JavaType.STRING => key
                  case _ => throw new RuntimeException(s"Unsupported type for key for ${fd.getName}")
                }
                mapEntryCmp.fromFieldsMap(
                  Map(keyDescriptor -> keyObj, valueDescriptor -> parseSingleValue(mapEntryCmp, valueDescriptor, jValue)))
            }
          case _ => throw new JsonFormatException(
            s"Expected an object for map field ${fd.getJsonName} of ${fd.getContainingType.getName}")
        }
      } else if (fd.isRepeated) {
        value match {
          case JArray(vals) => vals.map(parseSingleValue(cmp, fd, _)).toVector
          case _ => throw new JsonFormatException(
            s"Expected an array for repeated field ${fd.getJsonName} of ${fd.getContainingType.getName}")
        }
      } else parseSingleValue(cmp, fd, value)
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

  // PRIVATE //

  @inline
  private def serializeField(fd: FieldDescriptor, value: Any): JValue = {
    val wlsrl = getWellKnownSerializer(fd)
    if (fd.isMapField) {
      JObject(
        value.asInstanceOf[Seq[GeneratedMessage]].map {
          v =>
            val key = v.getField(v.companion.descriptor.findFieldByNumber(1)).toString
            val valueDescriptor = v.companion.descriptor.findFieldByNumber(2)
            val value = v.getField(valueDescriptor)
            key -> serializeSingleValue(valueDescriptor, value)
        }: _*)
    } else if (fd.isRepeated) {
      JArray(value.asInstanceOf[Seq[Any]].map(serializeSingleValue(fd, _)).toList)
    } else if (wlsrl.isDefined) {
      val f = wlsrl.get
      f(value.asInstanceOf[GeneratedMessage])
    } else serializeSingleValue(fd, value)
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

  private def getWellKnownSerializer(fd: FieldDescriptor): Option[(GeneratedMessage) => JValue] = {
    if(fd.getJavaType == JavaType.MESSAGE) {
      fd.getMessageType.getFullName match {
        case BOOL_VALUE_NAME |
             INT32_VALUE_NAME |
             UINT32_VALUE_NAME |
             INT64_VALUE_NAME |
             UINT64_VALUE_NAME |
             STRING_VALUE_NAME |
             BYTES_VALUE_NAME |
             FLOAT_VALUE_NAME |
             DOUBLE_VALUE_NAME =>
          Some(writeWrapper)
        case TIMESTAMP_VALUE_NAME =>
          Some(writeTimestamp)
        case DURATION_VALUE_NAME =>
          Some(writeDuration)
        case _ => None
      }
    }else None
  }

  private def getWellKnownDeserializer(fd: FieldDescriptor, jValue: JValue) = fd.getFullName match {
    case BOOL_VALUE_NAME |
         INT32_VALUE_NAME |
         UINT32_VALUE_NAME |
         INT64_VALUE_NAME |
         UINT64_VALUE_NAME |
         STRING_VALUE_NAME |
         BYTES_VALUE_NAME |
         FLOAT_VALUE_NAME |
         DOUBLE_VALUE_NAME =>

    case _ =>
  }

  private def writeWrapper(m: GeneratedMessage): JValue = {
    val fd = m.companion.descriptor.findFieldByName("value")
    if(fd == null) throw new InvalidProtocolBufferException("Invalid Wrapper Type")
    serializeSingleValue(fd, m.getField(fd))
  }

  private def writeTimestamp(m: GeneratedMessage): JValue = {
    val value = Timestamp.parseFrom(m.toByteArray)
    JString(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(value.seconds, value.nanos)))
  }

  private def writeDuration(m: GeneratedMessage): JValue = {
    val value = Duration.parseFrom(m.toByteArray)
    if(value.nanos == 0) JString(f"${value.seconds}%ds")
    else JString(f"${value.seconds}%d.${value.nanos}%09ds")
  }

  implicit def protoToReader[T <: GeneratedMessage with Message[T] : GeneratedMessageCompanion]: Reader[T] =
    new Reader[T] {
      def read(value: JValue): T = fromJson(value)
    }

  implicit def protoToWriter[T <: GeneratedMessage with Message[T]]: Writer[T] = new Writer[T] {
    def write(obj: T): JValue = toJson(obj)
  }
}
