package com.trueaccord.scalapb.json

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{ EnumValueDescriptor, FieldDescriptor }
import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }
import org.json4s.JsonAST._
import org.json4s.{ Reader, Writer }

import scala.language.existentials
import scala.reflect.ClassTag

case class JsonFormatException(msg: String, cause: Exception) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

case class FormatRegistry(mapClass: Map[Class[_], (_ => JValue, JValue => _)] = Map.empty) {
  def registerWriter[T <: GeneratedMessage](writer: T => JValue, parser: JValue => T)(implicit ct: ClassTag[T]): FormatRegistry = {
    copy(mapClass + (ct.runtimeClass -> (writer, parser)))
  }

  def getWriter[T](klass: Class[_ <: T]): Option[T => JValue] = {
    mapClass.get(klass).map(_._1.asInstanceOf[T => JValue])
  }

  def getParser[T](klass: Class[_ <: T]): Option[JValue => T] = {
    mapClass.get(klass).map(_._2.asInstanceOf[JValue => T])
  }
}

class Printer(
  includingDefaultValueFields: Boolean = false,
  preservingProtoFieldNames: Boolean = false,
  formattingLongAsNumber: Boolean = false,
  formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry) {

  def print[A](m: GeneratedMessage): String = {
    import org.json4s.jackson.JsonMethods._
    compact(toJson(m))
  }

  def toJson[A <: GeneratedMessage](m: A): JValue = {
    formatRegistry.getWriter[A](m.getClass) match {
      case Some(f) => f(m)
      case None =>
        val b = List.newBuilder[JField]
        m.getAllFields
        b.sizeHint(m.companion.javaDescriptor.getFields.size)
        val i = m.companion.javaDescriptor.getFields.iterator
        while (i.hasNext) {
          val f = i.next()
          if (f.getType != FieldDescriptor.Type.GROUP) {
            val name = if (preservingProtoFieldNames) f.getName else f.getJsonName
            m.getField(f) match {
              case null => if (includingDefaultValueFields && f.getJavaType != JavaType.MESSAGE) {
                // We are never printing empty optional messages to prevent infinite recursion.
                b += JField(name, JsonFormat.defaultJValue(f))
              }
              case Nil if f.isRepeated =>
                if (includingDefaultValueFields) {
                  b += JField(name, if (f.isMapField) JObject() else JArray(Nil))
                }
              case v => b += JField(name, serializeField(f, v))
            }
          }
        }
        JObject(b.result())
    }
  }

  @inline
  private def serializeField(fd: FieldDescriptor, value: Any): JValue = {
    if (fd.isMapField) {
      JObject(
        value.asInstanceOf[Seq[GeneratedMessage]].map {
          v =>
            val keyDescriptor = v.companion.javaDescriptor.findFieldByNumber(1)
            val key = Option(v.getField(keyDescriptor)).getOrElse(JsonFormat.defaultValue(keyDescriptor)).toString
            val valueDescriptor = v.companion.javaDescriptor.findFieldByNumber(2)
            val value = Option(v.getField(valueDescriptor)).getOrElse(JsonFormat.defaultValue(valueDescriptor))
            key -> serializeSingleValue(valueDescriptor, value)
        }: _*)
    } else if (fd.isRepeated) {
      JArray(value.asInstanceOf[Seq[Any]].map(serializeSingleValue(fd, _)).toList)
    } else serializeSingleValue(fd, value)
  }

  @inline
  private def serializeSingleValue(fd: FieldDescriptor, value: Any): JValue = fd.getJavaType match {
    case JavaType.ENUM => JString(value.asInstanceOf[EnumValueDescriptor].getName)
    case JavaType.MESSAGE => toJson(value.asInstanceOf[GeneratedMessage])
    case JavaType.INT => JInt(value.asInstanceOf[Int])
    case JavaType.LONG => if (!formattingLongAsNumber) JString(value.asInstanceOf[Long].toString) else JInt(BigInt(value.asInstanceOf[Long]))
    case JavaType.DOUBLE => JDouble(value.asInstanceOf[Double])
    case JavaType.FLOAT => JDouble(value.asInstanceOf[Float])
    case JavaType.BOOLEAN => JBool(value.asInstanceOf[Boolean])
    case JavaType.STRING => JString(value.asInstanceOf[String])
    case JavaType.BYTE_STRING => JString(
      Base64Variants.getDefaultVariant.encode(value.asInstanceOf[ByteString].toByteArray))
  }
}

object Printer {
}

class Parser(formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry) {
  def fromJsonString[A <: GeneratedMessage with Message[A]](str: String)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    import org.json4s.jackson.JsonMethods._
    fromJson(parse(str))
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

    formatRegistry.getParser(cmp.defaultInstance.getClass) match {
      case Some(p) => p(value)
      case None =>
        value match {
          case JObject(fields) =>
            val values: Map[String, JValue] = fields.map(k => k._1 -> k._2).toMap

            val valueMap: Map[FieldDescriptor, Any] = (for {
              fd <- cmp.javaDescriptor.getFields.asScala
              jsValue <- values.get(fd.getJsonName)
            } yield (fd, parseValue(fd, jsValue))).toMap

            cmp.fromFieldsMap(valueMap)
          case _ =>
            throw new JsonFormatException(s"Expected an object, found ${value}")
        }
    }
  }

  protected def parseSingleValue(cmp: GeneratedMessageCompanion[_], fd: FieldDescriptor, value: JValue): Any = (fd.getJavaType, value) match {
    case (JavaType.ENUM, JString(s)) => fd.getEnumType.findValueByName(s)
    case (JavaType.MESSAGE, o: JValue) =>
      // The asInstanceOf[] is a lie: we actually have a companion of some other message (not A),
      // but this doesn't matter after erasure.
      fromJson(o)(cmp.messageCompanionForField(fd)
        .asInstanceOf[GeneratedMessageCompanion[T] forSome { type T <: GeneratedMessage with Message[T]}])
    case (JavaType.INT, JInt(x)) => x.intValue
    case (JavaType.INT, JDouble(x)) => x.intValue
    case (JavaType.INT, JDecimal(x)) => x.intValue
    case (JavaType.INT, JNull) => 0
    case (JavaType.LONG, JDecimal(x)) => x.longValue()
    case (JavaType.LONG, JString(x)) => x.toLong
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
}

object JsonFormat {
  val DefaultRegistry = FormatRegistry()
    .registerWriter(WellKnownTypes.writeDuration, jv => jv match {
      case JString(str) => WellKnownTypes.parseDuration(str)
      case _ => throw new JsonFormatException("Expected a string.")
    })

  val printer = new Printer()
  val parser = new Parser()

  def toJsonString[A <: GeneratedMessage](m: A): String = printer.print(m)

  def toJson[A <: GeneratedMessage](m: A): JValue = printer.toJson(m)

  def fromJson[A <: GeneratedMessage with Message[A] : GeneratedMessageCompanion](
    value: JValue): A = {
    parser.fromJson(value)
  }

  def fromJsonString[A <: GeneratedMessage with Message[A] : GeneratedMessageCompanion](
    str: String): A = {
    parser.fromJsonString(str)
  }

  implicit def protoToReader[T <: GeneratedMessage with Message[T] : GeneratedMessageCompanion]: Reader[T] =
    new Reader[T] {
      def read(value: JValue): T = parser.fromJson(value)
    }

  implicit def protoToWriter[T <: GeneratedMessage with Message[T]]: Writer[T] = new Writer[T] {
    def write(obj: T): JValue = printer.toJson(obj)
  }

  def defaultValue(fd: FieldDescriptor): Any = {
    require(fd.isOptional)
    fd.getJavaType match {
      case JavaType.INT => 0
      case JavaType.LONG => 0L
      case JavaType.FLOAT | JavaType.DOUBLE => 0.0
      case JavaType.BOOLEAN => false
      case JavaType.STRING => ""
      case JavaType.BYTE_STRING => ByteString.EMPTY
      case JavaType.ENUM => fd.getEnumType.getValues.get(0)
      case JavaType.MESSAGE => throw new RuntimeException("No default value for message")
    }
  }

  def defaultJValue(fd: FieldDescriptor): JValue = {
    require(fd.isOptional)
    fd.getJavaType match {
      case JavaType.INT | JavaType.LONG => JInt(0)
      case JavaType.FLOAT | JavaType.DOUBLE => JDouble(0)
      case JavaType.BOOLEAN => JBool(false)
      case JavaType.STRING => JString("")
      case JavaType.BYTE_STRING => JString("")
      case JavaType.ENUM => JString(fd.getEnumType.getValues.get(0).getName)
      case JavaType.MESSAGE => throw new RuntimeException("No default value for message")
    }
  }
}
