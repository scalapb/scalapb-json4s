package com.trueaccord.scalapb.json

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.ByteString
import com.google.protobuf.duration.Duration
import com.google.protobuf.struct.NullValue
import com.google.protobuf.timestamp.Timestamp
import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import org.json4s.JsonAST._
import org.json4s.{Reader, Writer}

import scala.collection.mutable
import scala.language.existentials
import scala.reflect.ClassTag
import scalapb.descriptors._

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

  private type FieldBuilder = mutable.Builder[JField, List[JField]]

  private def serializeMessageField(fd: FieldDescriptor,
                                    name: String,
                                    value: Any,
                                    b: FieldBuilder): Unit = {
    value match {
      case null =>
      // We are never printing empty optional messages to prevent infinite recursion.
      case Nil =>
        if (includingDefaultValueFields) {
          b += JField(name, if (fd.isMapField) JObject() else JArray(Nil))
        }
      case xs: Seq[GeneratedMessage] @unchecked =>
        if (fd.isMapField) {
          val mapEntryDescriptor = fd.scalaType.asInstanceOf[ScalaType.Message].descriptor
          val keyDescriptor = mapEntryDescriptor.findFieldByNumber(1).get
          val valueDescriptor = mapEntryDescriptor.findFieldByNumber(2).get
          b += JField(name, JObject(
            xs.map {
              x =>
                val key = x.getField(keyDescriptor) match {
                  case PBoolean(v) => v.toString
                  case PDouble(v) => v.toString
                  case PFloat(v) => v.toString
                  case PInt(v) => v.toString
                  case PLong(v) => v.toString
                  case PString(v) => v
                  case v => throw new JsonFormatException(s"Unexpected value for key: $v")
                }
                val value = if (valueDescriptor.protoType.isTypeMessage) {
                  toJson(x.getFieldByNumber(valueDescriptor.number).asInstanceOf[GeneratedMessage])
                } else {
                  JsonFormat.serializeSingleValue(valueDescriptor, x.getField(valueDescriptor), formattingLongAsNumber)
                }
                key -> value
            }: _*))
        } else {
          b += JField(name, JArray(xs.map(toJson).toList))
        }
      case msg: GeneratedMessage =>
        b += JField(name, toJson(msg))
      case v =>
        throw new JsonFormatException(v.toString)
    }
  }

  private def serializeNonMessageField(fd: FieldDescriptor, name: String, value: PValue, b: FieldBuilder) = {
    value match {
      case PEmpty => if (includingDefaultValueFields) {
        b += JField(name, defaultJValue(fd))
      }
      case PRepeated(xs) =>
        if (xs.nonEmpty || includingDefaultValueFields) {
          b += JField(name, JArray(xs.map(JsonFormat.serializeSingleValue(fd, _, formattingLongAsNumber)).toList))
        }
      case v =>
        if (includingDefaultValueFields ||
          !fd.isOptional ||
          !fd.file.isProto3 ||
          (v != JsonFormat.defaultValue(fd))) {
          b += JField(name, JsonFormat.serializeSingleValue(fd, v, formattingLongAsNumber))
        }
    }
  }

  def toJson[A <: GeneratedMessage](m: A): JValue = {
    formatRegistry.getWriter[A](m.getClass) match {
      case Some(f) => f(m)
      case None =>
        val b = List.newBuilder[JField]
        val descriptor = m.companion.scalaDescriptor
        b.sizeHint(descriptor.fields.size)
        descriptor.fields.foreach {
          f =>
            val name = if (preservingProtoFieldNames) f.name else f.asProto.getJsonName
            if (f.protoType.isTypeMessage) {
              serializeMessageField(f, name, m.getFieldByNumber(f.number), b)
            } else {
              serializeNonMessageField(f, name, m.getField(f), b)
            }
        }
        JObject(b.result())
    }
  }

  private def defaultJValue(fd: FieldDescriptor): JValue = JsonFormat.serializeSingleValue(
    fd, JsonFormat.defaultValue(fd), formattingLongAsNumber)
}

class Parser(
  preservingProtoFieldNames: Boolean = false,
  formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry) {

  def fromJsonString[A <: GeneratedMessage with Message[A]](str: String)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    import org.json4s.jackson.JsonMethods._
    fromJson(parse(str))
  }

  def fromJson[A <: GeneratedMessage with Message[A]](value: JValue)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    cmp.messageReads.read(fromJsonToPMessage(cmp, value))
  }

  protected def serializedName(fd: FieldDescriptor): String = {
    if (preservingProtoFieldNames) fd.asProto.getName else fd.asProto.getJsonName
  }

  private def fromJsonToPMessage(cmp: GeneratedMessageCompanion[_], value: JValue): PMessage = {

    def parseValue(fd: FieldDescriptor, value: JValue): PValue = {
      if (fd.isMapField) {
        value match {
          case JObject(vals) =>
            val mapEntryDesc = fd.scalaType.asInstanceOf[ScalaType.Message].descriptor
            val keyDescriptor = mapEntryDesc.findFieldByNumber(1).get
            val valueDescriptor = mapEntryDesc.findFieldByNumber(2).get
            PRepeated(vals.map {
              case (key, jValue) =>
                val keyObj = keyDescriptor.scalaType match {
                  case ScalaType.Boolean => PBoolean(java.lang.Boolean.valueOf(key))
                  case ScalaType.Double => PDouble(java.lang.Double.valueOf(key))
                  case ScalaType.Float => PFloat(java.lang.Float.valueOf(key))
                  case ScalaType.Int => PInt(java.lang.Integer.valueOf(key))
                  case ScalaType.Long => PLong(java.lang.Long.valueOf(key))
                  case ScalaType.String => PString(key)
                  case _ => throw new RuntimeException(s"Unsupported type for key for ${fd.name}")
                }
                PMessage(
                  Map(keyDescriptor -> keyObj,
                    valueDescriptor -> parseSingleValue(cmp.messageCompanionForFieldNumber(fd.number), valueDescriptor, jValue)))
            }(scala.collection.breakOut))
          case _ => throw new JsonFormatException(
            s"Expected an object for map field ${serializedName(fd)} of ${fd.containingMessage.name}")
        }
      } else if (fd.isRepeated) {
        value match {
          case JArray(vals) => PRepeated(vals.map(parseSingleValue(cmp, fd, _)).toVector)
          case _ => throw new JsonFormatException(
            s"Expected an array for repeated field ${serializedName(fd)} of ${fd.containingMessage.name}")
        }
      } else parseSingleValue(cmp, fd, value)
    }

    formatRegistry.getParser(cmp.defaultInstance.getClass) match {
      case Some(p) => p(value).asInstanceOf[GeneratedMessage].toPMessage
      case None =>
        value match {
          case JObject(fields) =>
            val values: Map[String, JValue] = fields.map(k => k._1 -> k._2).toMap

            val valueMap: Map[FieldDescriptor, PValue] = (for {
              fd <- cmp.scalaDescriptor.fields
              jsValue <- values.get(serializedName(fd))
            } yield (fd, parseValue(fd, jsValue))).toMap

            PMessage(valueMap)
          case _ =>
            throw new JsonFormatException(s"Expected an object, found ${value}")
        }
    }
  }

  protected def parseSingleValue(containerCompanion: GeneratedMessageCompanion[_], fd: FieldDescriptor, value: JValue): PValue = (fd.scalaType, value) match {
    case (ScalaType.Enum(ed), JNull) if ed == NullValue.scalaDescriptor => PEnum(NullValue.NULL_VALUE.scalaValueDescriptor)
    case (ScalaType.Enum(ed), JInt(v)) => PEnum(ed.findValueByNumber(v.toInt).getOrElse(throw new JsonFormatException(s"Invalid enum value: ${v.toInt} for enum type: ${ed.fullName}")))
    case (ScalaType.Enum(ed), JString(s)) =>
      PEnum(ed.values.find(_.name == s).getOrElse(throw new JsonFormatException(s"Unrecognized enum value '${s}'")))
    case (ScalaType.Message(md), o: JValue) =>
      fromJsonToPMessage(containerCompanion.messageCompanionForFieldNumber(fd.number), o)
    case (st, v) => JsonFormat.parsePrimitiveByScalaType(st, v,
      throw new JsonFormatException(
        s"Unexpected value ($value) for field ${serializedName(fd)} of ${fd.containingMessage.name}"))
  }
}

object JsonFormat {
  import com.google.protobuf.wrappers
  val DefaultRegistry = FormatRegistry()
    .registerWriter((d: Duration) => JString(Durations.writeDuration(d)), jv => jv match {
      case JString(str) => Durations.parseDuration(str)
      case _ => throw new JsonFormatException("Expected a string.")
    })
    .registerWriter((t: Timestamp) => JString(Timestamps.writeTimestamp(t)), jv => jv match {
      case JString(str) => Timestamps.parseTimestamp(str)
      case _ => throw new JsonFormatException("Expected a string.")
    })
    .registerWriter[wrappers.DoubleValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.DoubleValue])
    .registerWriter[wrappers.FloatValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.FloatValue])
    .registerWriter[wrappers.Int32Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.Int32Value])
    .registerWriter[wrappers.Int64Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.Int64Value])
    .registerWriter[wrappers.UInt32Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.UInt32Value])
    .registerWriter[wrappers.UInt64Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.UInt64Value])
    .registerWriter[wrappers.BoolValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.BoolValue])
    .registerWriter[wrappers.BytesValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.BytesValue])
    .registerWriter[wrappers.StringValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.StringValue])
    .registerWriter[com.google.protobuf.struct.Value](StructFormat.structValueWriter, StructFormat.structValueParser)
    .registerWriter[com.google.protobuf.struct.Struct](StructFormat.structWriter, StructFormat.structParser)
    .registerWriter[com.google.protobuf.struct.ListValue](StructFormat.listValueWriter, StructFormat.listValueParser)

  def primitiveWrapperWriter[T <: GeneratedMessage with Message[T]](implicit cmp: GeneratedMessageCompanion[T]): (T => JValue) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    t => serializeSingleValue(fieldDesc, t.getField(fieldDesc), formattingLongAsNumber = false)
  }

  def primitiveWrapperParser[T <: GeneratedMessage with Message[T]](implicit cmp: GeneratedMessageCompanion[T]): (JValue => T) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    jv => cmp.messageReads.read(PMessage(Map(fieldDesc -> JsonFormat.parsePrimitiveByScalaType(
      fieldDesc.scalaType, jv, throw new JsonFormatException(s"Unexpected value for ${cmp.scalaDescriptor.name}")))))
  }

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

  def defaultValue(fd: FieldDescriptor): PValue = {
    require(fd.isOptional)
    fd.scalaType match {
      case ScalaType.Int => PInt(0)
      case ScalaType.Long => PLong(0L)
      case ScalaType.Float => PFloat(0)
      case ScalaType.Double => PDouble(0)
      case ScalaType.Boolean => PBoolean(false)
      case ScalaType.String => PString("")
      case ScalaType.ByteString => PByteString(ByteString.EMPTY)
      case ScalaType.Enum(ed) => PEnum(ed.values(0))
      case ScalaType.Message(_) => throw new RuntimeException("No default value for message")
    }
  }

  def parsePrimitiveByScalaType(scalaType: ScalaType, value: JValue, onError: => PValue): PValue = (scalaType, value) match {
    case (ScalaType.Int, JInt(x)) => PInt(x.intValue)
    case (ScalaType.Int, JDouble(x)) => PInt(x.intValue)
    case (ScalaType.Int, JDecimal(x)) => PInt(x.intValue)
    case (ScalaType.Int, JNull) => PInt(0)
    case (ScalaType.Long, JLong(x)) => PLong(x.toLong)
    case (ScalaType.Long, JDecimal(x)) => PLong(x.longValue())
    case (ScalaType.Long, JString(x)) => PLong(x.toLong)
    case (ScalaType.Long, JInt(x)) => PLong(x.toLong)
    case (ScalaType.Long, JNull) => PLong(0L)
    case (ScalaType.Double, JDouble(x)) => PDouble(x)
    case (ScalaType.Double, JInt(x)) => PDouble(x.toDouble)
    case (ScalaType.Double, JDecimal(x)) => PDouble(x.toDouble)
    case (ScalaType.Double, JNull) => PDouble(0.toDouble)
    case (ScalaType.Float, JDouble(x)) => PFloat(x.toFloat)
    case (ScalaType.Float, JInt(x)) => PFloat(x.toFloat)
    case (ScalaType.Float, JDecimal(x)) => PFloat(x.toFloat)
    case (ScalaType.Float, JNull) => PFloat(0.toFloat)
    case (ScalaType.Boolean, JBool(b)) => PBoolean(b)
    case (ScalaType.Boolean, JNull) => PBoolean(false)
    case (ScalaType.String, JString(s)) => PString(s)
    case (ScalaType.String, JNull) => PString("")
    case (ScalaType.ByteString, JString(s)) =>
      PByteString(ByteString.copyFrom(Base64Variants.getDefaultVariant.decode(s)))
    case (ScalaType.ByteString, JNull) => PByteString(ByteString.EMPTY)
    case _ => onError
  }

  def serializeSingleValue(fd: FieldDescriptor, value: PValue, formattingLongAsNumber: Boolean): JValue = value match {
    case PEnum(e) =>
      if (e.containingEnum == NullValue.scalaDescriptor)
        JNull
      else JString(e.name)
    case PInt(v) => JInt(v)
    case PLong(v) => if (formattingLongAsNumber) JLong(v) else JString(v.toString)
    case PDouble(v) => JDouble(v)
    case PFloat(v) => JDouble(v)
    case PBoolean(v) => JBool(v)
    case PString(v) => JString(v)
    case PByteString(v) => JString(Base64Variants.getDefaultVariant.encode(v.toByteArray))
    case _: PMessage | PRepeated(_) | PEmpty => throw new RuntimeException("Should not happen")
  }
}
