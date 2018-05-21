package scalapb.json4s

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.ByteString
import com.google.protobuf.descriptor.FieldDescriptorProto
import com.google.protobuf.duration.Duration
import com.google.protobuf.struct.NullValue
import com.google.protobuf.timestamp.Timestamp
import scalapb.json4s.JsonFormat.GenericCompanion
import scalapb._
import org.json4s.JsonAST._
import org.json4s.{Reader, Writer}

import scala.collection.mutable
import scala.language.existentials
import scala.reflect.ClassTag
import _root_.scalapb.descriptors._

case class JsonFormatException(msg: String, cause: Exception) extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
}

case class Formatter[T](
  writer: (Printer, T) => JValue, parser: (Parser, JValue) => T)

case class FormatRegistry(
  messageFormatters: Map[Class[_], Formatter[_]] = Map.empty,
  enumFormatters: Map[EnumDescriptor, Formatter[EnumValueDescriptor]] = Map.empty,
  registeredCompanions: Seq[GenericCompanion] = Seq.empty) {

  def registerMessageFormatter[T <: GeneratedMessage](
    writer: (Printer, T) => JValue,
    parser: (Parser, JValue) => T)(implicit ct: ClassTag[T]): FormatRegistry = {
    copy(messageFormatters = messageFormatters + (ct.runtimeClass -> Formatter(writer, parser)))
  }

  def registerEnumFormatter[E <: GeneratedEnum](
    writer: (Printer, EnumValueDescriptor) => JValue,
    parser: (Parser, JValue) => EnumValueDescriptor)(implicit cmp: GeneratedEnumCompanion[E]): FormatRegistry = {
    copy(enumFormatters = enumFormatters + (cmp.scalaDescriptor -> Formatter(writer, parser)))
  }

  def registerWriter[T <: GeneratedMessage : ClassTag](writer: T => JValue, parser: JValue => T): FormatRegistry = {
    registerMessageFormatter((p: Printer, t: T) => writer(t), ((p: Parser, v: JValue) => parser(v)))
  }

  def getMessageWriter[T](klass: Class[_ <: T]): Option[(Printer, T) => JValue] = {
    messageFormatters.get(klass).asInstanceOf[Option[Formatter[T]]].map(_.writer)
  }

  def getMessageParser[T](klass: Class[_ <: T]): Option[(Parser, JValue) => T] = {
    messageFormatters.get(klass).asInstanceOf[Option[Formatter[T]]].map(_.parser)
  }

  def getEnumWriter(descriptor: EnumDescriptor): Option[(Printer, EnumValueDescriptor) => JValue] = {
    enumFormatters.get(descriptor).map(_.writer)
  }

  def getEnumParser(descriptor: EnumDescriptor): Option[(Parser, JValue) => EnumValueDescriptor] = {
    enumFormatters.get(descriptor).map(_.parser)
  }
}

/** TypeRegistry is used to map the @type field in Any messages to a ScalaPB generated message.
  *
  * You need to
  */
case class TypeRegistry(companions: Map[String, GenericCompanion] = Map.empty, private val filesSeen: Set[String]=Set.empty) {
  def addMessage[T <: GeneratedMessage with Message[T]](implicit cmp: GeneratedMessageCompanion[T]): TypeRegistry = {
    addMessageByCompanion(cmp)
  }

  def addFile(file: GeneratedFileObject): TypeRegistry = {
    if (filesSeen.contains(file.scalaDescriptor.fullName)) this else {
      val withFileSeen = copy(filesSeen = filesSeen + (file.scalaDescriptor.fullName))

      val withDeps: TypeRegistry =
        file.dependencies.foldLeft(withFileSeen)((r, f) => r.addFile(f))

      file.messagesCompanions.foldLeft(withDeps)((r, mc) => r.addMessageByCompanion(mc.asInstanceOf[GenericCompanion]))
    }
  }

  def addMessageByCompanion(cmp: GenericCompanion): TypeRegistry = {
    // TODO: need to add contained file to follow JsonFormat
    val withNestedMessages =
      cmp.nestedMessagesCompanions.foldLeft(this)((r, mc) => r.addMessageByCompanion(mc.asInstanceOf[GenericCompanion]))
    copy(companions = withNestedMessages.companions + ((TypeRegistry.TypePrefix + cmp.scalaDescriptor.fullName) -> cmp))
  }

  def findType(typeName: String): Option[GenericCompanion] = companions.get(typeName)
}

object TypeRegistry {
  private val TypePrefix = "type.googleapis.com/"

  def empty = TypeRegistry(Map.empty)
}


class Printer(
  includingDefaultValueFields: Boolean = false,
  preservingProtoFieldNames: Boolean = false,
  formattingLongAsNumber: Boolean = false,
  formattingEnumsAsNumber: Boolean = false,
  formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry,
  val typeRegistry: TypeRegistry = TypeRegistry.empty) {
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
      case xs: Iterable[GeneratedMessage] @unchecked =>
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
                  serializeSingleValue(valueDescriptor, x.getField(valueDescriptor), formattingLongAsNumber)
                }
                key -> value
            }.toSeq: _*))
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
      case PEmpty => if (includingDefaultValueFields && fd.containingOneof.isEmpty) {
        b += JField(name, defaultJValue(fd))
      }
      case PRepeated(xs) =>
        if (xs.nonEmpty || includingDefaultValueFields) {
          b += JField(name, JArray(xs.map(serializeSingleValue(fd, _, formattingLongAsNumber)).toList))
        }
      case v =>
        if (includingDefaultValueFields ||
          !fd.isOptional ||
          !fd.file.isProto3 ||
          (v != JsonFormat.defaultValue(fd)) ||
          fd.containingOneof.isDefined) {
          b += JField(name, serializeSingleValue(fd, v, formattingLongAsNumber))
        }
    }
  }

  def toJson[A <: GeneratedMessage](m: A): JValue = {
    formatRegistry.getMessageWriter[A](m.getClass) match {
      case Some(f) => f(this, m)
      case None =>
        val b = List.newBuilder[JField]
        val descriptor = m.companion.scalaDescriptor
        b.sizeHint(descriptor.fields.size)
        descriptor.fields.foreach {
          f =>
            val name = if (preservingProtoFieldNames) f.name else JsonFormat.jsonName(f)
            if (f.protoType.isTypeMessage) {
              serializeMessageField(f, name, m.getFieldByNumber(f.number), b)
            } else {
              serializeNonMessageField(f, name, m.getField(f), b)
            }
        }
        JObject(b.result())
    }
  }

  private def defaultJValue(fd: FieldDescriptor): JValue = serializeSingleValue(
    fd, JsonFormat.defaultValue(fd), formattingLongAsNumber)

  private def unsignedInt(n: Int): Long = n & 0x00000000FFFFFFFFL
  private def unsignedLong(n: Long): BigInt =
    if (n < 0) BigInt(n & 0x7FFFFFFFFFFFFFFFL).setBit(63) else BigInt(n)

  private def formatLong(n: Long, protoType: FieldDescriptorProto.Type, formattingLongAsNumber: Boolean): JValue = {
    val v: BigInt = if (protoType.isTypeUint64 || protoType.isTypeFixed64) unsignedLong(n) else BigInt(n)
    if (formattingLongAsNumber) JInt(v) else JString(v.toString())
  }

  def serializeSingleValue(fd: FieldDescriptor, value: PValue, formattingLongAsNumber: Boolean): JValue = value match {
    case PEnum(e) =>
      formatRegistry.getEnumWriter(e.containingEnum) match {
        case Some(writer) => writer(this, e)
        case None => if (formattingEnumsAsNumber) JInt(e.number) else JString(e.name)
      }
    case PInt(v) if fd.protoType.isTypeUint32 => JInt(unsignedInt(v))
    case PInt(v) if fd.protoType.isTypeFixed32 => JInt(unsignedInt(v))
    case PInt(v) => JInt(v)
    case PLong(v) => formatLong(v, fd.protoType, formattingLongAsNumber)
    case PDouble(v) => JDouble(v)
    case PFloat(v) => JDouble(v)
    case PBoolean(v) => JBool(v)
    case PString(v) => JString(v)
    case PByteString(v) => JString(Base64Variants.getDefaultVariant.encode(v.toByteArray))
    case _: PMessage | PRepeated(_) | PEmpty => throw new RuntimeException("Should not happen")
  }
}

class Parser(
  preservingProtoFieldNames: Boolean = false,
  formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry,
  val typeRegistry: TypeRegistry = TypeRegistry.empty) {

  def fromJsonString[A <: GeneratedMessage with Message[A]](str: String)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    import org.json4s.jackson.JsonMethods._
    fromJson(parse(str))
  }

  def fromJson[A <: GeneratedMessage with Message[A]](value: JValue)(
    implicit cmp: GeneratedMessageCompanion[A]): A = {
    cmp.messageReads.read(fromJsonToPMessage(cmp, value))
  }

  private def serializedName(fd: FieldDescriptor): String = {
    if (preservingProtoFieldNames) fd.asProto.getName else JsonFormat.jsonName(fd)
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

    formatRegistry.getMessageParser(cmp.defaultInstance.getClass) match {
      case Some(p) => p(this, value).asInstanceOf[GeneratedMessage].toPMessage
      case None =>
        value match {
          case JObject(fields) =>
            val values: Map[String, JValue] = fields.map(k => k._1 -> k._2).toMap

            val valueMap: Map[FieldDescriptor, PValue] = (for {
              fd <- cmp.scalaDescriptor.fields
              jsValue <- values.get(serializedName(fd)) if jsValue != JNull
            } yield (fd, parseValue(fd, jsValue))).toMap

            PMessage(valueMap)
          case _ =>
            throw new JsonFormatException(s"Expected an object, found ${value}")
        }
    }
  }

  def defaultEnumParser(enumDescriptor: EnumDescriptor, value: JValue): EnumValueDescriptor = value match {
    case JInt(v) => enumDescriptor.findValueByNumber(v.toInt).getOrElse(throw new JsonFormatException(s"Invalid enum value: ${v.toInt} for enum type: ${enumDescriptor.fullName}"))
    case JString(s) =>
      enumDescriptor.values.find(_.name == s).getOrElse(throw new JsonFormatException(s"Unrecognized enum value '${s}'"))
    case _ =>
      throw new JsonFormatException(
        s"Unexpected value ($value) for enum ${enumDescriptor.fullName}")
  }

  protected def parseSingleValue(containerCompanion: GeneratedMessageCompanion[_], fd: FieldDescriptor, value: JValue): PValue = fd.scalaType match {
    case ScalaType.Enum(ed) =>
      PEnum(formatRegistry.getEnumParser(ed) match {
        case Some(parser) => parser(this, value)
        case None => defaultEnumParser(ed, value)
      })
    case ScalaType.Message(md) =>
      fromJsonToPMessage(containerCompanion.messageCompanionForFieldNumber(fd.number), value)
    case st => JsonFormat.parsePrimitive(st, fd.protoType, value,
      throw new JsonFormatException(
        s"Unexpected value ($value) for field ${serializedName(fd)} of ${fd.containingMessage.name}"))
  }
}

object JsonFormat {
  import com.google.protobuf.wrappers

  type GenericCompanion = GeneratedMessageCompanion[T] forSome { type T <: GeneratedMessage with Message[T] }

  val DefaultRegistry = FormatRegistry()
    .registerWriter((d: Duration) => JString(Durations.writeDuration(d)), jv => jv match {
      case JString(str) => Durations.parseDuration(str)
      case _ => throw new JsonFormatException("Expected a string.")
    })
    .registerWriter((t: Timestamp) => JString(Timestamps.writeTimestamp(t)), jv => jv match {
      case JString(str) => Timestamps.parseTimestamp(str)
      case _ => throw new JsonFormatException("Expected a string.")
    })
    .registerMessageFormatter[wrappers.DoubleValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.DoubleValue])
    .registerMessageFormatter[wrappers.FloatValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.FloatValue])
    .registerMessageFormatter[wrappers.Int32Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.Int32Value])
    .registerMessageFormatter[wrappers.Int64Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.Int64Value])
    .registerMessageFormatter[wrappers.UInt32Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.UInt32Value])
    .registerMessageFormatter[wrappers.UInt64Value](primitiveWrapperWriter, primitiveWrapperParser[wrappers.UInt64Value])
    .registerMessageFormatter[wrappers.BoolValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.BoolValue])
    .registerMessageFormatter[wrappers.BytesValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.BytesValue])
    .registerMessageFormatter[wrappers.StringValue](primitiveWrapperWriter, primitiveWrapperParser[wrappers.StringValue])
    .registerEnumFormatter[NullValue]((_, _) => JNull, (parser, value) => value match {
      case JNull => NullValue.NULL_VALUE.scalaValueDescriptor
      case _ => parser.defaultEnumParser(NullValue.scalaDescriptor, value)
    })
    .registerWriter[com.google.protobuf.struct.Value](StructFormat.structValueWriter, StructFormat.structValueParser)
    .registerWriter[com.google.protobuf.struct.Struct](StructFormat.structWriter, StructFormat.structParser)
    .registerWriter[com.google.protobuf.struct.ListValue](StructFormat.listValueWriter, StructFormat.listValueParser)
    .registerMessageFormatter[com.google.protobuf.any.Any](AnyFormat.anyWriter, AnyFormat.anyParser)

  def primitiveWrapperWriter[T <: GeneratedMessage with Message[T]](implicit cmp: GeneratedMessageCompanion[T]): ((Printer, T) => JValue) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    (printer, t) => printer.serializeSingleValue(fieldDesc, t.getField(fieldDesc), formattingLongAsNumber = false)
  }

  def primitiveWrapperParser[T <: GeneratedMessage with Message[T]](implicit cmp: GeneratedMessageCompanion[T]): ((Parser, JValue) => T) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    (parser, jv) => cmp.messageReads.read(PMessage(Map(fieldDesc -> JsonFormat.parsePrimitive(
      fieldDesc.scalaType, fieldDesc.protoType, jv, throw new JsonFormatException(s"Unexpected value for ${cmp.scalaDescriptor.name}")))))
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

  def parsePrimitive(scalaType: ScalaType, protoType: FieldDescriptorProto.Type, value: JValue, onError: => PValue): PValue = (scalaType, value) match {
    case (ScalaType.Int, JInt(x)) => PInt(x.intValue)
    case (ScalaType.Int, JDouble(x)) => PInt(x.intValue)
    case (ScalaType.Int, JDecimal(x)) => PInt(x.intValue)
    case (ScalaType.Int, JString(x)) if protoType.isTypeInt32 => parseInt32(x)
    case (ScalaType.Int, JString(x)) if protoType.isTypeSint32 => parseInt32(x)
    case (ScalaType.Int, JString(x)) => parseUint32(x)
    case (ScalaType.Long, JLong(x)) => PLong(x.toLong)
    case (ScalaType.Long, JDecimal(x)) => PLong(x.longValue())
    case (ScalaType.Long, JString(x)) if protoType.isTypeInt64 => parseInt64(x)
    case (ScalaType.Long, JString(x)) if protoType.isTypeSint64 => parseInt64(x)
    case (ScalaType.Long, JString(x)) => parseUint64(x)
    case (ScalaType.Long, JInt(x)) => PLong(x.toLong)
    case (ScalaType.Double, JDouble(x)) => PDouble(x)
    case (ScalaType.Double, JInt(x)) => PDouble(x.toDouble)
    case (ScalaType.Double, JDecimal(x)) => PDouble(x.toDouble)
    case (ScalaType.Double, JString("NaN")) => PDouble(Double.NaN)
    case (ScalaType.Double, JString("Infinity")) => PDouble(Double.PositiveInfinity)
    case (ScalaType.Double, JString("-Infinity")) => PDouble(Double.NegativeInfinity)
    case (ScalaType.Float, JDouble(x)) => PFloat(x.toFloat)
    case (ScalaType.Float, JInt(x)) => PFloat(x.toFloat)
    case (ScalaType.Float, JDecimal(x)) => PFloat(x.toFloat)
    case (ScalaType.Float, JString("NaN")) => PFloat(Float.NaN)
    case (ScalaType.Float, JString("Infinity")) => PFloat(Float.PositiveInfinity)
    case (ScalaType.Float, JString("-Infinity")) => PFloat(Float.NegativeInfinity)
    case (ScalaType.Boolean, JBool(b)) => PBoolean(b)
    case (ScalaType.String, JString(s)) => PString(s)
    case (ScalaType.ByteString, JString(s)) =>
      PByteString(ByteString.copyFrom(Base64Variants.getDefaultVariant.decode(s)))
    case _ => onError
  }

  def parseBigDecimal(value: String): BigDecimal = {
    try {
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      BigDecimal(value)
    } catch { case e: Exception =>
      throw JsonFormatException(s"Not a numeric value: $value", e)
    }
  }

  def parseInt32(value: String): PValue = {
    try {
      PInt(value.toInt)
    } catch { case _: Exception =>
      try {
        PInt(parseBigDecimal(value).toIntExact)
      } catch { case e: Exception =>
        throw JsonFormatException(s"Not an int32 value: $value", e)
      }
    }
  }

  def parseInt64(value: String): PValue = {
    try {
      PLong(value.toLong)
    } catch { case _: Exception =>
      val bd = parseBigDecimal(value)
      try {
        PLong(bd.toLongExact)
      } catch { case e: Exception =>
        throw JsonFormatException(s"Not an int64 value: $value", e)
      }
    }
  }

  def parseUint32(value: String): PValue = {
    try {
      val result = value.toLong
      if (result < 0 || result > 0xFFFFFFFFl) throw new JsonFormatException(s"Out of range uint32 value: $value")
      return PInt(result.toInt)
    } catch {
      case e: JsonFormatException => throw e
      case e: Exception => // Fall through.
    }
    parseBigDecimal(value).toBigIntExact().map { intVal =>
      if (intVal < 0 || intVal > 0xFFFFFFFFl) throw new JsonFormatException(s"Out of range uint32 value: $value")
      PLong(intVal.intValue())
    } getOrElse {
      throw new JsonFormatException(s"Not an uint32 value: $value")
    }
  }

  val MAX_UINT64 = BigInt("FFFFFFFFFFFFFFFF", 16)

  def parseUint64(value: String): PValue = {
    parseBigDecimal(value).toBigIntExact().map { intVal =>
      if (intVal < 0 || intVal > MAX_UINT64) {
        throw new JsonFormatException(s"Out of range uint64 value: $value")
      }
      PLong(intVal.longValue())
    } getOrElse {
      throw new JsonFormatException(s"Not an uint64 value: $value")
    }
  }

  def jsonName(fd: FieldDescriptor): String = {
    // protoc<3 doesn't know about json_name, so we fill it in if it's not populated.
    fd.asProto.jsonName.getOrElse(NameUtils.snakeCaseToCamelCase(fd.asProto.getName))
  }
}
