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
import org.json4s.{Reader, Writer, MappingException}

import scala.collection.mutable
import scala.collection.concurrent.TrieMap
import scala.language.existentials
import scala.reflect.ClassTag
import _root_.scalapb.descriptors._
import com.google.protobuf.descriptor.FieldDescriptorProto.Type
import com.google.protobuf.field_mask.FieldMask

import scala.util.Try

class JsonFormatException(msg: String, cause: Exception)
    extends MappingException(msg, cause) {
  def this(msg: String) = this(msg, null)
}

case class Formatter[T](
    writer: (Printer, T) => JValue,
    parser: (Parser, JValue) => T
)

case class EnumFormatter[T](
    writer: (Printer, T) => JValue,
    parser: (Parser, JValue) => Option[T]
)

case class FormatRegistry(
    messageFormatters: Map[GeneratedMessageCompanion[_], Formatter[_]] =
      Map.empty,
    enumFormatters: Map[EnumDescriptor, EnumFormatter[EnumValueDescriptor]] =
      Map.empty,
    registeredCompanions: Seq[GenericCompanion] = Seq.empty
) {
  private def companionForClass[T <: GeneratedMessage](
      ct: Class[_]
  ): GeneratedMessageCompanion[T] = {
    ct
      .getMethod("companion")
      .getReturnType()
      .getField("MODULE$")
      .get(null)
      .asInstanceOf[GeneratedMessageCompanion[T]]
  }

  def registerMessageFormatter[T <: GeneratedMessage](
      writer: (Printer, T) => JValue,
      parser: (Parser, JValue) => T
  )(implicit ct: ClassTag[T]): FormatRegistry = {
    copy(
      messageFormatters =
        messageFormatters + (companionForClass[T](ct.runtimeClass) -> Formatter(
          writer,
          parser
        ))
    )
  }

  def registerEnumFormatter[E <: GeneratedEnum](
      writer: (Printer, EnumValueDescriptor) => JValue,
      parser: (Parser, JValue) => Option[EnumValueDescriptor]
  )(implicit cmp: GeneratedEnumCompanion[E]): FormatRegistry = {
    copy(
      enumFormatters = enumFormatters + (cmp.scalaDescriptor -> EnumFormatter(
        writer,
        parser
      ))
    )
  }

  def registerWriter[T <: GeneratedMessage: ClassTag](
      writer: T => JValue,
      parser: JValue => T
  ): FormatRegistry = {
    registerMessageFormatter(
      (p: Printer, t: T) => writer(t),
      ((p: Parser, v: JValue) => parser(v))
    )
  }

  def getMessageWriter[T <: GeneratedMessage](implicit
      cmp: GeneratedMessageCompanion[T]
  ): Option[(Printer, T) => JValue] = {
    messageFormatters
      .get(cmp)
      .asInstanceOf[Option[Formatter[T]]]
      .map(_.writer)
  }

  @deprecated(
    "Use getMessageWriter that takes an implicit GeneratedMessageCompanion",
    "0.10.2"
  )
  def getMessageWriter[T](
      klass: Class[_ <: T]
  ): Option[(Printer, T) => JValue] = {
    messageFormatters
      .get(companionForClass(klass))
      .asInstanceOf[Option[Formatter[T]]]
      .map(_.writer)
  }

  def getMessageParser[T <: GeneratedMessage](implicit
      cmp: GeneratedMessageCompanion[T]
  ): Option[(Parser, JValue) => T] = {
    messageFormatters
      .get(cmp)
      .asInstanceOf[Option[Formatter[T]]]
      .map(_.parser)
  }

  @deprecated(
    "Use getMessageParser that takes an implicit GeneratedMessageCompanion",
    "0.10.2"
  )
  def getMessageParser[T](
      klass: Class[_ <: T]
  ): Option[(Parser, JValue) => T] = {
    messageFormatters
      .get(companionForClass(klass))
      .asInstanceOf[Option[Formatter[T]]]
      .map(_.parser)
  }

  def getEnumWriter(
      descriptor: EnumDescriptor
  ): Option[(Printer, EnumValueDescriptor) => JValue] = {
    enumFormatters.get(descriptor).map(_.writer)
  }

  def getEnumParser(
      descriptor: EnumDescriptor
  ): Option[(Parser, JValue) => Option[EnumValueDescriptor]] = {
    enumFormatters.get(descriptor).map(_.parser)
  }
}

/** TypeRegistry is used to map the @type field in Any messages to a ScalaPB
  * generated message.
  *
  * TypeRegistries are added to Printers and Parsers to enable printing and
  * parsing of Any messages.
  */
case class TypeRegistry(
    companions: Map[String, GenericCompanion] = Map.empty,
    private val filesSeen: Set[String] = Set.empty
) {
  def addMessage[T <: GeneratedMessage](implicit
      cmp: GeneratedMessageCompanion[T]
  ): TypeRegistry = {
    addMessageByCompanion(cmp)
  }

  def addFile(file: GeneratedFileObject): TypeRegistry = {
    if (filesSeen.contains(file.scalaDescriptor.fullName)) this
    else {
      val withFileSeen = copy(
        filesSeen = filesSeen + (file.scalaDescriptor.fullName)
      )

      val withDeps: TypeRegistry =
        file.dependencies.foldLeft(withFileSeen)((r, f) => r.addFile(f))

      file.messagesCompanions.foldLeft(withDeps)((r, mc) =>
        r.addMessageByCompanion(mc.asInstanceOf[GenericCompanion])
      )
    }
  }

  def addMessageByCompanion(cmp: GenericCompanion): TypeRegistry = {
    // TODO: need to add contained file to follow JsonFormat
    val withNestedMessages =
      cmp.nestedMessagesCompanions.foldLeft(this)((r, mc) =>
        r.addMessageByCompanion(mc.asInstanceOf[GenericCompanion])
      )
    copy(
      companions =
        withNestedMessages.companions + ((TypeRegistry.TypePrefix + cmp.scalaDescriptor.fullName) -> cmp)
    )
  }

  def findType(typeName: String): Option[GenericCompanion] =
    companions.get(typeName)
}

object TypeRegistry {
  private val TypePrefix = "type.googleapis.com/"

  def empty = TypeRegistry(Map.empty)

  val default = TypeRegistry()
    .addFile(com.google.protobuf.struct.StructProto)
    .addFile(com.google.protobuf.wrappers.WrappersProto)
    .addFile(com.google.protobuf.field_mask.FieldMaskProto)
    .addMessage(com.google.protobuf.any.Any)
    .addMessage(com.google.protobuf.timestamp.Timestamp)
    .addMessage(com.google.protobuf.duration.Duration)
    .addMessage(com.google.protobuf.empty.Empty)
}

object Printer {
  private final case class PrinterConfig(
      isIncludingDefaultValueFields: Boolean,
      isPreservingProtoFieldNames: Boolean,
      isFormattingLongAsNumber: Boolean,
      isFormattingEnumsAsNumber: Boolean,
      isFormattingMapEntriesAsKeyValuePairs: Boolean,
      formatRegistry: FormatRegistry,
      typeRegistry: TypeRegistry
  )

  private def defaultConfig =
    PrinterConfig(
      isIncludingDefaultValueFields = false,
      isPreservingProtoFieldNames = false,
      isFormattingLongAsNumber = false,
      isFormattingEnumsAsNumber = false,
      isFormattingMapEntriesAsKeyValuePairs = false,
      formatRegistry = JsonFormat.DefaultRegistry,
      typeRegistry = TypeRegistry.default
    )
}

class Printer private (config: Printer.PrinterConfig) {
  def this() = this(Printer.defaultConfig)

  @deprecated(
    "Use new Printer() and chain includingDefaultValueFields, preservingProtoFieldNames, etc.",
    "0.7.2"
  )
  def this(
      includingDefaultValueFields: Boolean = false,
      preservingProtoFieldNames: Boolean = false,
      formattingLongAsNumber: Boolean = false,
      formattingEnumsAsNumber: Boolean = false,
      mapEntriesAsKeyValuePairs: Boolean = false,
      formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry,
      typeRegistry: TypeRegistry = TypeRegistry.empty
  ) =
    this(
      Printer.PrinterConfig(
        isIncludingDefaultValueFields = includingDefaultValueFields,
        isPreservingProtoFieldNames = preservingProtoFieldNames,
        isFormattingLongAsNumber = formattingLongAsNumber,
        isFormattingEnumsAsNumber = formattingEnumsAsNumber,
        isFormattingMapEntriesAsKeyValuePairs = mapEntriesAsKeyValuePairs,
        formatRegistry = formatRegistry,
        typeRegistry = typeRegistry
      )
    )

  def includingDefaultValueFields: Printer =
    new Printer(config.copy(isIncludingDefaultValueFields = true))

  def preservingProtoFieldNames: Printer =
    new Printer(config.copy(isPreservingProtoFieldNames = true))

  def formattingLongAsNumber: Printer =
    new Printer(config.copy(isFormattingLongAsNumber = true))

  def formattingEnumsAsNumber: Printer =
    new Printer(config.copy(isFormattingEnumsAsNumber = true))

  def mapEntriesAsKeyValuePairs: Printer =
    new Printer(config.copy(isFormattingMapEntriesAsKeyValuePairs = true))

  def withFormatRegistry(formatRegistry: FormatRegistry): Printer =
    new Printer(config.copy(formatRegistry = formatRegistry))

  def withTypeRegistry(typeRegistry: TypeRegistry): Printer =
    new Printer(config.copy(typeRegistry = typeRegistry))

  def typeRegistry: TypeRegistry = config.typeRegistry

  def print[A](m: GeneratedMessage): String = {
    import org.json4s.jackson.JsonMethods._
    compact(toJson(m))
  }

  private type FieldBuilder = mutable.Builder[JField, List[JField]]

  private def serializeMessageField(
      fd: FieldDescriptor,
      name: String,
      value: Any,
      b: FieldBuilder
  ): Unit = {
    value match {
      case null =>
      // We are never printing empty optional messages to prevent infinite recursion.
      case Nil =>
        if (config.isIncludingDefaultValueFields) {
          b += JField(
            name,
            if (fd.isMapField && !config.isFormattingMapEntriesAsKeyValuePairs)
              JObject()
            else JArray(Nil)
          )
        }
      case xs: Iterable[GeneratedMessage] @unchecked =>
        if (fd.isMapField && !config.isFormattingMapEntriesAsKeyValuePairs) {
          val mapEntryDescriptor =
            fd.scalaType.asInstanceOf[ScalaType.Message].descriptor
          val keyDescriptor = mapEntryDescriptor.findFieldByNumber(1).get
          val valueDescriptor = mapEntryDescriptor.findFieldByNumber(2).get
          b += JField(
            name,
            JObject(xs.map { x =>
              val key = x.getField(keyDescriptor) match {
                case PBoolean(v) => v.toString
                case PDouble(v)  => v.toString
                case PFloat(v)   => v.toString
                case PInt(v)     => v.toString
                case PLong(v)    => v.toString
                case PString(v)  => v
                case v           =>
                  throw new JsonFormatException(s"Unexpected value for key: $v")
              }
              val value = if (valueDescriptor.protoType.isTypeMessage) {
                toJson(
                  x.getFieldByNumber(valueDescriptor.number)
                    .asInstanceOf[GeneratedMessage]
                )
              } else {
                serializeSingleValue(
                  valueDescriptor,
                  x.getField(valueDescriptor)
                )
              }
              key -> value
            }.toSeq: _*)
          )
        } else {
          b += JField(name, JArray(xs.map(toJson).toList))
        }
      case msg: GeneratedMessage =>
        b += JField(name, toJson(msg))
      case v =>
        throw new JsonFormatException(v.toString)
    }
  }

  private def serializeNonMessageField(
      fd: FieldDescriptor,
      name: String,
      value: PValue,
      b: FieldBuilder
  ) = {
    value match {
      case PEmpty =>
        if (
          config.isIncludingDefaultValueFields && fd.containingOneof.isEmpty
        ) {
          b += JField(name, defaultJValue(fd))
        }
      case PRepeated(xs) =>
        if (xs.nonEmpty || config.isIncludingDefaultValueFields) {
          b += JField(
            name,
            JArray(
              xs.map(
                serializeSingleValue(fd, _)
              ).toList
            )
          )
        }
      case v =>
        if (
          config.isIncludingDefaultValueFields ||
          !fd.isOptional ||
          !fd.file.isProto3 ||
          (v != JsonFormat.defaultValue(fd)) ||
          fd.containingOneof.isDefined
        ) {
          b += JField(
            name,
            serializeSingleValue(fd, v)
          )
        }
    }
  }

  def toJson[A <: GeneratedMessage](m: A): JValue = {
    config.formatRegistry.getMessageWriter[A](
      m.companion.asInstanceOf[GeneratedMessageCompanion[A]]
    ) match {
      case Some(f) => f(this, m)
      case None    =>
        val b = List.newBuilder[JField]
        val descriptor = m.companion.scalaDescriptor
        b.sizeHint(descriptor.fields.size)
        descriptor.fields.foreach { f =>
          if (f.asProto.getType != FieldDescriptorProto.Type.TYPE_GROUP) {
            val name =
              if (config.isPreservingProtoFieldNames) f.name
              else JsonFormat.jsonName(f)
            if (f.protoType.isTypeMessage) {
              serializeMessageField(f, name, m.getFieldByNumber(f.number), b)
            } else {
              serializeNonMessageField(f, name, m.getField(f), b)
            }
          }
        }
        JObject(b.result())
    }
  }

  private def defaultJValue(fd: FieldDescriptor): JValue =
    serializeSingleValue(
      fd,
      JsonFormat.defaultValue(fd)
    )

  private def unsignedInt(n: Int): Long = n & 0x00000000ffffffffL
  private def unsignedLong(n: Long): BigInt =
    if (n < 0) BigInt(n & 0x7fffffffffffffffL).setBit(63) else BigInt(n)

  private def formatLong(
      n: Long,
      protoType: FieldDescriptorProto.Type,
      formattingLongAsNumber: Boolean
  ): JValue = {
    val v: BigInt =
      if (protoType.isTypeUint64 || protoType.isTypeFixed64) unsignedLong(n)
      else BigInt(n)
    if (formattingLongAsNumber) JInt(v) else JString(v.toString())
  }

  def serializeSingleValue(fd: FieldDescriptor, value: PValue): JValue = {
    serializeSingleValue(fd, value, config.isFormattingLongAsNumber)
  }

  def serializeSingleValue(
      fd: FieldDescriptor,
      value: PValue,
      formattingLongAsNumber: Boolean
  ): JValue =
    value match {
      case PEnum(e) =>
        config.formatRegistry.getEnumWriter(e.containingEnum) match {
          case Some(writer) => writer(this, e)
          case None         =>
            if (config.isFormattingEnumsAsNumber || e.isUnrecognized)
              JInt(e.number)
            else JString(e.name)
        }
      case PInt(v) if fd.protoType.isTypeUint32  => JInt(unsignedInt(v))
      case PInt(v) if fd.protoType.isTypeFixed32 => JInt(unsignedInt(v))
      case PInt(v)                               => JInt(v)
      case PLong(v)   => formatLong(v, fd.protoType, formattingLongAsNumber)
      case PDouble(v) => JDouble(v)
      case PFloat(v)  =>
        if (!v.isNaN && !v.isInfinite) JDecimal(BigDecimal.decimal(v))
        else JDouble(v)
      case PBoolean(v)    => JBool(v)
      case PString(v)     => JString(v)
      case PByteString(v) =>
        JString(Base64Variants.getDefaultVariant.encode(v.toByteArray))
      case _: PMessage | PRepeated(_) | PEmpty =>
        throw new RuntimeException("Should not happen")
    }
}

object Parser {
  private final case class ParserConfig(
      isIgnoringUnknownFields: Boolean,
      isIgnoringOverlappingOneofFields: Boolean,
      mapEntriesAsKeyValuePairs: Boolean,
      formatRegistry: FormatRegistry,
      typeRegistry: TypeRegistry
  )
}

class Parser private (config: Parser.ParserConfig) {
  def this() =
    this(
      Parser.ParserConfig(
        isIgnoringUnknownFields = false,
        isIgnoringOverlappingOneofFields = false,
        mapEntriesAsKeyValuePairs = false,
        JsonFormat.DefaultRegistry,
        TypeRegistry.default
      )
    )

  @deprecated(
    "Use new Parser() and chain with usingTypeRegistry or formatRegistry",
    "0.7.1"
  )
  def this(
      preservingProtoFieldNames: Boolean = false,
      formatRegistry: FormatRegistry = JsonFormat.DefaultRegistry,
      typeRegistry: TypeRegistry = TypeRegistry.empty
  ) =
    this(
      Parser.ParserConfig(
        isIgnoringUnknownFields = false,
        isIgnoringOverlappingOneofFields = false,
        mapEntriesAsKeyValuePairs = false,
        formatRegistry,
        typeRegistry
      )
    )

  def ignoringUnknownFields: Parser =
    new Parser(config.copy(isIgnoringUnknownFields = true))

  def ignoringOverlappingOneofFields: Parser =
    new Parser(config.copy(isIgnoringOverlappingOneofFields = true))

  def mapEntriesAsKeyValuePairs: Parser =
    new Parser(config.copy(mapEntriesAsKeyValuePairs = true))

  def withFormatRegistry(formatRegistry: FormatRegistry) =
    new Parser(config.copy(formatRegistry = formatRegistry))

  def withTypeRegistry(typeRegistry: TypeRegistry) =
    new Parser(config.copy(typeRegistry = typeRegistry))

  def typeRegistry: TypeRegistry = config.typeRegistry

  def fromJsonString[A <: GeneratedMessage: GeneratedMessageCompanion](
      str: String
  ): A = {
    import org.json4s.jackson.JsonMethods._
    fromJson(parse(str, useBigDecimalForDouble = true))
  }

  def fromJson[A <: GeneratedMessage: GeneratedMessageCompanion](
      value: JValue
  ): A = {
    fromJson(value, false)
  }

  private[json4s] def fromJson[A <: GeneratedMessage](
      value: JValue,
      skipTypeUrl: Boolean
  )(implicit cmp: GeneratedMessageCompanion[A]): A = {
    cmp.messageReads.read(fromJsonToPMessage(cmp, value, skipTypeUrl))
  }

  private def allowNullValueForField(fd: FieldDescriptor): Boolean = {
    fd.scalaType match {
      case ScalaType.Message(t) =>
        t == com.google.protobuf.struct.Value.scalaDescriptor
      case _ => false
    }
  }

  private def fromJsonToPMessage(
      cmp: GeneratedMessageCompanion[_],
      value: JValue,
      skipTypeUrl: Boolean
  ): PMessage = {
    def parseValue(fd: FieldDescriptor, value: JValue): PValue = {
      if (fd.isMapField && !config.mapEntriesAsKeyValuePairs) {
        value match {
          case JObject(vals) =>
            val mapEntryDesc =
              fd.scalaType.asInstanceOf[ScalaType.Message].descriptor
            val keyDescriptor = mapEntryDesc.findFieldByNumber(1).get
            val valueDescriptor = mapEntryDesc.findFieldByNumber(2).get
            PRepeated(vals.iterator.map { case (key, jValue) =>
              val keyObj = keyDescriptor.scalaType match {
                case ScalaType.Boolean =>
                  PBoolean(java.lang.Boolean.valueOf(key))
                case ScalaType.Double =>
                  PDouble(java.lang.Double.valueOf(key))
                case ScalaType.Float  => PFloat(java.lang.Float.valueOf(key))
                case ScalaType.Int    => PInt(java.lang.Integer.valueOf(key))
                case ScalaType.Long   => PLong(java.lang.Long.valueOf(key))
                case ScalaType.String => PString(key)
                case _                =>
                  throw new RuntimeException(
                    s"Unsupported type for key for ${fd.name}"
                  )
              }
              PMessage(
                Map(
                  keyDescriptor -> keyObj,
                  valueDescriptor -> parseSingleValue(
                    cmp.messageCompanionForFieldNumber(fd.number),
                    valueDescriptor,
                    jValue
                  )
                )
              )
            }.toVector)
          case _ =>
            throw new JsonFormatException(
              s"Expected an object for map field ${fd.name} of ${fd.containingMessage.name}"
            )
        }
      } else if (fd.isRepeated) {
        value match {
          case JArray(vals) =>
            PRepeated(vals.map(parseSingleValue(cmp, fd, _)).toVector)
          case _ =>
            throw new JsonFormatException(
              s"Expected an array for repeated field ${fd.name} of ${fd.containingMessage.name}"
            )
        }
      } else parseSingleValue(cmp, fd, value)
    }

    config.formatRegistry.getMessageParser(
      cmp.asInstanceOf[GeneratedMessageCompanion[GeneratedMessage]]
    ) match {
      case Some(p) => p(this, value).toPMessage
      case None    =>
        value match {
          case JObject(fields) =>
            val usedOneofs = mutable.Set[OneofDescriptor]()
            val fieldMap = JsonFormat.MemorizedFieldNameMap(cmp.scalaDescriptor)
            val valueMapBuilder = Map.newBuilder[FieldDescriptor, PValue]
            fields.foreach { case (name: String, jValue: JValue) =>
              if (fieldMap.contains(name)) {
                val fd = fieldMap(name)
                if (jValue != JNull || allowNullValueForField(fd)) {
                  fd.containingOneof.foreach(o =>
                    if (
                      !config.isIgnoringOverlappingOneofFields && !usedOneofs
                        .add(o)
                    ) {
                      throw new JsonFormatException(
                        s"Overlapping field '${name}' in oneof"
                      )
                    }
                  )
                  valueMapBuilder += (fd -> parseValue(fd, jValue))
                }
              } else if (
                !config.isIgnoringUnknownFields && !(skipTypeUrl && name == "@type")
              ) {
                throw new JsonFormatException(
                  s"Cannot find field: ${name} in message ${cmp.scalaDescriptor.fullName}"
                )
              }
            }

            PMessage(valueMapBuilder.result())
          case _ =>
            throw new JsonFormatException(s"Expected an object, found ${value}")
        }
    }
  }

  def defaultEnumParser(
      enumDescriptor: EnumDescriptor,
      value: JValue
  ): Option[EnumValueDescriptor] = {
    def enumValueFromInt(v: Int): Option[EnumValueDescriptor] =
      if (enumDescriptor.file.isProto3)
        Some(enumDescriptor.findValueByNumberCreatingIfUnknown(v))
      else
        enumDescriptor.findValueByNumber(v)

    def fail() =
      throw new JsonFormatException(
        s"Invalid enum value: $value for enum type: ${enumDescriptor.fullName}"
      )

    def defaultValue =
      if (config.isIgnoringUnknownFields && enumDescriptor.file.isProto3)
        enumDescriptor.findValueByNumber(0)
      else None

    val res = value match {
      case JDecimal(v) =>
        try {
          enumValueFromInt(v.bigDecimal.intValueExact)
        } catch {
          case e: ArithmeticException => defaultValue
        }
      case JInt(v) =>
        enumValueFromInt(v.toInt)
      case JString(s) =>
        enumDescriptor.values
          .find(_.name == s)
          .orElse {
            try {
              enumValueFromInt(new java.math.BigDecimal(s).intValueExact)
            } catch {
              case _: ArithmeticException   => None
              case _: NumberFormatException => None
            }
          }
          .orElse(defaultValue)
      case _ => fail()
    }

    if (res.isEmpty && !config.isIgnoringUnknownFields) {
      fail()
    }

    res
  }

  protected def parseSingleValue(
      containerCompanion: GeneratedMessageCompanion[_],
      fd: FieldDescriptor,
      value: JValue
  ): PValue =
    fd.scalaType match {
      case ScalaType.Enum(ed) => {
        val res: Option[EnumValueDescriptor] =
          config.formatRegistry.getEnumParser(ed) match {
            case Some(parser) => parser(this, value)
            case None         => defaultEnumParser(ed, value)
          }

        res.fold[PValue](PEmpty)(PEnum.apply)
      }
      case ScalaType.Message(md) =>
        fromJsonToPMessage(
          containerCompanion.messageCompanionForFieldNumber(fd.number),
          value,
          false
        )
      case st =>
        JsonFormat.parsePrimitive(
          fd.protoType,
          value,
          throw new JsonFormatException(
            s"Unexpected value ($value) for field ${fd.name} of ${fd.containingMessage.name}"
          )
        )
    }
}

object JsonFormat {
  import com.google.protobuf.wrappers

  // type GenericCompanion = GeneratedMessageCompanion[T] forSome { type T <: GeneratedMessage}
  type GenericCompanion = GeneratedMessageCompanion[_ <: GeneratedMessage]

  val DefaultRegistry = FormatRegistry()
    .registerWriter(
      (d: Duration) => JString(Durations.writeDuration(d)),
      jv =>
        jv match {
          case JString(str) => Durations.parseDuration(str)
          case _ => throw new JsonFormatException("Expected a string.")
        }
    )
    .registerWriter(
      (t: Timestamp) => JString(Timestamps.writeTimestamp(t)),
      jv =>
        jv match {
          case JString(str) => Timestamps.parseTimestamp(str)
          case _ => throw new JsonFormatException("Expected a string.")
        }
    )
    .registerWriter(
      (m: FieldMask) => JString(FieldMaskUtil.toJsonString(m)),
      jv =>
        jv match {
          case JString(str) => FieldMaskUtil.fromJsonString(str)
          case _ => throw new JsonFormatException("Expected a string.")
        }
    )
    .registerMessageFormatter[wrappers.DoubleValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.DoubleValue]
    )
    .registerMessageFormatter[wrappers.FloatValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.FloatValue]
    )
    .registerMessageFormatter[wrappers.Int32Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.Int32Value]
    )
    .registerMessageFormatter[wrappers.Int64Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.Int64Value]
    )
    .registerMessageFormatter[wrappers.UInt32Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.UInt32Value]
    )
    .registerMessageFormatter[wrappers.UInt64Value](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.UInt64Value]
    )
    .registerMessageFormatter[wrappers.BoolValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.BoolValue]
    )
    .registerMessageFormatter[wrappers.BytesValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.BytesValue]
    )
    .registerMessageFormatter[wrappers.StringValue](
      primitiveWrapperWriter,
      primitiveWrapperParser[wrappers.StringValue]
    )
    .registerEnumFormatter[NullValue](
      (_, _) => JNull,
      (parser, value) =>
        value match {
          case JNull => Some(NullValue.NULL_VALUE.scalaValueDescriptor)
          case _ => parser.defaultEnumParser(NullValue.scalaDescriptor, value)
        }
    )
    .registerWriter[com.google.protobuf.struct.Value](
      StructFormat.structValueWriter,
      StructFormat.structValueParser
    )
    .registerWriter[com.google.protobuf.struct.Struct](
      StructFormat.structWriter,
      StructFormat.structParser
    )
    .registerWriter[com.google.protobuf.struct.ListValue](
      StructFormat.listValueWriter,
      StructFormat.listValueParser
    )
    .registerMessageFormatter[com.google.protobuf.any.Any](
      AnyFormat.anyWriter,
      AnyFormat.anyParser
    )

  def primitiveWrapperWriter[T <: GeneratedMessage](implicit
      cmp: GeneratedMessageCompanion[T]
  ): ((Printer, T) => JValue) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    (printer, t) =>
      printer.serializeSingleValue(
        fieldDesc,
        t.getField(fieldDesc)
      )
  }

  def primitiveWrapperParser[T <: GeneratedMessage](implicit
      cmp: GeneratedMessageCompanion[T]
  ): ((Parser, JValue) => T) = {
    val fieldDesc = cmp.scalaDescriptor.findFieldByNumber(1).get
    (parser, jv) =>
      cmp.messageReads.read(
        PMessage(
          Map(
            fieldDesc -> JsonFormat.parsePrimitive(
              fieldDesc.protoType,
              jv,
              throw new JsonFormatException(
                s"Unexpected value for ${cmp.scalaDescriptor.name}"
              )
            )
          )
        )
      )
  }

  val printer = new Printer()
  val parser = new Parser()

  def toJsonString[A <: GeneratedMessage](m: A): String = printer.print(m)

  def toJson[A <: GeneratedMessage](m: A): JValue = printer.toJson(m)

  def fromJson[A <: GeneratedMessage: GeneratedMessageCompanion](
      value: JValue
  ): A = parser.fromJson(value)

  def fromJsonEither[A <: GeneratedMessage: GeneratedMessageCompanion](
      value: JValue
  ): Either[MappingException, A] = try { Right(parser.fromJson(value)) }
  catch {
    case e: JsonFormatException => Left(new MappingException("fail", e))
  }

  def fromJsonString[A <: GeneratedMessage: GeneratedMessageCompanion](
      str: String
  ): A = {
    parser.fromJsonString(str)
  }

  implicit def protoToReader[T <: GeneratedMessage: GeneratedMessageCompanion]
      : Reader[T] = Reader.from(fromJsonEither(_))

  implicit def protoToWriter[T <: GeneratedMessage]: Writer[T] =
    new Writer[T] {
      def write(obj: T): JValue = printer.toJson(obj)
    }

  def defaultValue(fd: FieldDescriptor): PValue = {
    require(fd.isOptional)
    fd.scalaType match {
      case ScalaType.Int        => PInt(0)
      case ScalaType.Long       => PLong(0L)
      case ScalaType.Float      => PFloat(0)
      case ScalaType.Double     => PDouble(0)
      case ScalaType.Boolean    => PBoolean(false)
      case ScalaType.String     => PString("")
      case ScalaType.ByteString => PByteString(ByteString.EMPTY)
      case ScalaType.Enum(ed)   => PEnum(ed.values(0))
      case ScalaType.Message(_) =>
        throw new RuntimeException("No default value for message")
    }
  }

  @deprecated("Use parsePrimitive(protoType, value, onError) instead.", "0.9.0")
  def parsePrimitive(
      scalaType: ScalaType,
      protoType: FieldDescriptorProto.Type,
      value: JValue,
      onError: => PValue
  ): PValue =
    parsePrimitive(protoType, value, onError)

  def parsePrimitive(
      protoType: FieldDescriptorProto.Type,
      value: JValue,
      onError: => PValue
  ): PValue =
    (protoType, value) match {
      case (Type.TYPE_UINT32 | Type.TYPE_FIXED32, JInt(x)) =>
        parseUint32(x.toString)
      case (Type.TYPE_UINT32 | Type.TYPE_FIXED32, JDouble(x)) =>
        parseUint32(x.toString)
      case (Type.TYPE_UINT32 | Type.TYPE_FIXED32, JDecimal(x)) =>
        parseUint32(x.toString)
      case (Type.TYPE_UINT32 | Type.TYPE_FIXED32, JString(x)) => parseUint32(x)
      case (Type.TYPE_SINT32 | Type.TYPE_INT32 | Type.TYPE_SFIXED32, JInt(x)) =>
        parseInt32(x.toString)
      case (
            Type.TYPE_SINT32 | Type.TYPE_INT32 | Type.TYPE_SFIXED32,
            JDouble(x)
          ) =>
        parseInt32(x.toString)
      case (
            Type.TYPE_SINT32 | Type.TYPE_INT32 | Type.TYPE_SFIXED32,
            JDecimal(x)
          ) =>
        parseInt32(x.toString)
      case (
            Type.TYPE_SINT32 | Type.TYPE_INT32 | Type.TYPE_SFIXED32,
            JString(x)
          ) =>
        parseInt32(x)

      case (Type.TYPE_UINT64 | Type.TYPE_FIXED64, JInt(x)) =>
        parseUint64(x.toString)
      case (Type.TYPE_UINT64 | Type.TYPE_FIXED64, JDouble(x)) =>
        parseUint64(x.toString)
      case (Type.TYPE_UINT64 | Type.TYPE_FIXED64, JDecimal(x)) =>
        parseUint64(x.toString)
      case (Type.TYPE_UINT64 | Type.TYPE_FIXED64, JString(x)) => parseUint64(x)
      case (Type.TYPE_SINT64 | Type.TYPE_INT64 | Type.TYPE_SFIXED64, JInt(x)) =>
        parseInt64(x.toString)
      case (
            Type.TYPE_SINT64 | Type.TYPE_INT64 | Type.TYPE_SFIXED64,
            JDouble(x)
          ) =>
        parseInt64(x.toString)
      case (
            Type.TYPE_SINT64 | Type.TYPE_INT64 | Type.TYPE_SFIXED64,
            JDecimal(x)
          ) =>
        parseInt64(x.toString)
      case (
            Type.TYPE_SINT64 | Type.TYPE_INT64 | Type.TYPE_SFIXED64,
            JString(x)
          ) =>
        parseInt64(x)

      case (Type.TYPE_DOUBLE, JDouble(x))     => parseDouble(x.toString)
      case (Type.TYPE_DOUBLE, JInt(x))        => parseDouble(x.toString)
      case (Type.TYPE_DOUBLE, JDecimal(x))    => parseDouble(x.toString)
      case (Type.TYPE_DOUBLE, JString(v))     => parseDouble(v)
      case (Type.TYPE_FLOAT, JDouble(x))      => parseFloat(x.toString)
      case (Type.TYPE_FLOAT, JInt(x))         => parseFloat(x.toString)
      case (Type.TYPE_FLOAT, JDecimal(x))     => parseFloat(x.toString)
      case (Type.TYPE_FLOAT, JString(v))      => parseFloat(v)
      case (Type.TYPE_BOOL, JBool(b))         => PBoolean(b)
      case (Type.TYPE_BOOL, JString("true"))  => PBoolean(true)
      case (Type.TYPE_BOOL, JString("false")) => PBoolean(false)
      case (Type.TYPE_STRING, JString(s))     => PString(s)
      case (Type.TYPE_BYTES, JString(s))      =>
        PByteString(
          ByteString.copyFrom(Base64Variants.getDefaultVariant.decode(s))
        )
      case _ => onError
    }

  def parseBigDecimal(value: String): BigDecimal = {
    try {
      // JSON doesn't distinguish between integer values and floating point values so "1" and
      // "1.000" are treated as equal in JSON. For this reason we accept floating point values for
      // integer fields as well as long as it actually is an integer (i.e., round(value) == value).
      BigDecimal(value)
    } catch {
      case e: Exception =>
        throw new JsonFormatException(s"Not a numeric value: $value", e)
    }
  }

  def parseInt32(value: String): PValue = {
    try {
      PInt(value.toInt)
    } catch {
      case _: Exception =>
        try {
          PInt(parseBigDecimal(value).toIntExact)
        } catch {
          case e: Exception =>
            throw new JsonFormatException(s"Not an int32 value: $value", e)
        }
    }
  }

  def parseInt64(value: String): PValue = {
    try {
      PLong(value.toLong)
    } catch {
      case _: Exception =>
        val bd = parseBigDecimal(value)
        try {
          PLong(bd.toLongExact)
        } catch {
          case e: Exception =>
            throw new JsonFormatException(s"Not an int64 value: $value", e)
        }
    }
  }

  def parseUint32(value: String): PValue = {
    try {
      val result = value.toLong
      if (result < 0 || result > 0xffffffffL)
        throw new JsonFormatException(s"Out of range uint32 value: $value")
      return PInt(result.toInt)
    } catch {
      case e: JsonFormatException => throw e
      case e: Exception           => // Fall through.
    }
    parseBigDecimal(value).toBigIntExact.map { intVal =>
      if (intVal < 0 || intVal > 0xffffffffL)
        throw new JsonFormatException(s"Out of range uint32 value: $value")
      PInt(intVal.intValue)
    } getOrElse {
      throw new JsonFormatException(s"Not an uint32 value: $value")
    }
  }

  val MAX_UINT64 = BigInt("FFFFFFFFFFFFFFFF", 16)

  def parseUint64(value: String): PValue = {
    parseBigDecimal(value).toBigIntExact.map { intVal =>
      if (intVal < 0 || intVal > MAX_UINT64) {
        throw new JsonFormatException(s"Out of range uint64 value: $value")
      }
      PLong(intVal.longValue)
    } getOrElse {
      throw new JsonFormatException(s"Not an uint64 value: $value")
    }
  }

  def parseDouble(value: String): PDouble =
    value match {
      case "NaN"       => PDouble(Double.NaN)
      case "Infinity"  => PDouble(Double.PositiveInfinity)
      case "-Infinity" => PDouble(Double.NegativeInfinity)
      case v           =>
        try {
          val bd = new java.math.BigDecimal(v)
          if (bd.compareTo(MAX_DOUBLE) > 0 || bd.compareTo(MIN_DOUBLE) < 0) {
            throw new JsonFormatException("Out of range double value: " + v)
          }
          PDouble(bd.doubleValue)
        } catch {
          case e: JsonFormatException => throw e
          case e: Exception           =>
            throw new JsonFormatException("Not a double value: " + v)
        }
    }

  def parseFloat(str: String): PFloat =
    str match {
      case "NaN"       => PFloat(Float.NaN)
      case "Infinity"  => PFloat(Float.PositiveInfinity)
      case "-Infinity" => PFloat(Float.NegativeInfinity)
      case v           =>
        try {
          val value = java.lang.Double.parseDouble(v)
          if (
            (value > Float.MaxValue * (1.0 + EPSILON)) ||
            (value < -Float.MaxValue * (1.0 + EPSILON))
          ) {
            throw new JsonFormatException("Out of range float value: " + value)
          }
          PFloat(value.toFloat)
        } catch {
          case e: JsonFormatException => throw e
          case e: Exception           =>
            throw new JsonFormatException("Not a float value: " + v)
        }
    }

  def jsonName(fd: FieldDescriptor): String = {
    // protoc<3 doesn't know about json_name, so we fill it in if it's not populated.
    fd.asProto.jsonName
      .getOrElse(NameUtils.snakeCaseToCamelCase(fd.asProto.getName))
  }

  /** Given a message descriptor, provides a map from field names to field
    * descriptors.
    */
  private[json4s] object MemorizedFieldNameMap {
    private[this] val fieldNameMap
        : TrieMap[Descriptor, Map[String, FieldDescriptor]] =
      TrieMap.empty

    def apply(descriptor: Descriptor): Map[String, FieldDescriptor] = {
      fieldNameMap.getOrElseUpdate(
        descriptor, {
          val mapBuilder = Map.newBuilder[String, FieldDescriptor]
          descriptor.fields.foreach { fd =>
            mapBuilder += fd.name -> fd
            mapBuilder += JsonFormat.jsonName(fd) -> fd
          }
          mapBuilder.result()
        }
      )
    }
  }

  // From protobuf-java's JsonFormat.java:
  val EPSILON: Double = 1e-6

  val MORE_THAN_ONE =
    new java.math.BigDecimal(String.valueOf(1.toDouble + EPSILON))

  val MAX_DOUBLE = new java.math.BigDecimal(String.valueOf(Double.MaxValue))
    .multiply(MORE_THAN_ONE)

  val MIN_DOUBLE = new java.math.BigDecimal(String.valueOf(Double.MinValue))
    .multiply(MORE_THAN_ONE)
}
