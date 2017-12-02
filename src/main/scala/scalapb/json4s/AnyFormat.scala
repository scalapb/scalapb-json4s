package scalapb.json4s

import com.google.protobuf.any.{Any => PBAny}
import org.json4s.JsonAST.{JNothing, JObject, JString, JValue}

import scala.language.existentials

object AnyFormat {
  val anyWriter: (Printer, PBAny) => JValue = { case (printer, any) =>
    // Find the companion so it can be used to JSON-serialize the message. Perhaps this can be circumvented by
    // including the original GeneratedMessage with the Any (at least in memory).
    val cmp = printer.typeRegistry.findType(any.typeUrl)
      .getOrElse(throw new IllegalStateException(
        s"Unknown type ${any.typeUrl}; you may have to register it via FormatRegistry.registerCompanion"))

    // Unpack the message...
    val message = any.unpack(cmp)

    // ... and add the @type marker to the resulting JSON
    printer.toJson(message) match {
      case JObject(fields) => JObject(("@type" -> JString(any.typeUrl)) +: fields)
      case value =>
        // Safety net, this shouldn't happen
        throw new IllegalStateException(s"Message of type ${any.typeUrl} emitted non-object JSON: $value")
    }
  }

  val anyParser: (Parser, JValue) => PBAny = {
    case (parser, obj @ JObject(fields)) =>
      obj \ "@type" match {
        case JString(typeUrl) =>
          val cmp = parser.typeRegistry.findType(typeUrl)
            .getOrElse(throw new JsonFormatException(s"""Unknown type: "$typeUrl""""))
          val message = parser.fromJson(obj)(cmp)
          PBAny(typeUrl = typeUrl, value = message.toByteString)

        case JNothing =>
          throw new JsonFormatException(s"Missing type url when parsing $obj")

        case unknown =>
          throw new JsonFormatException(s"Expected string @type field, got $unknown")
      }

    case (_, unknown) =>
      throw new JsonFormatException(s"Expected an object, got $unknown")
  }
}
