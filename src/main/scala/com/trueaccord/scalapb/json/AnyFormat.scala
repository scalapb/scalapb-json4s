package com.trueaccord.scalapb.json

import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import org.json4s.JsonAST.{JNothing, JObject, JString, JValue}

import scala.reflect.runtime.universe

object AnyFormat {
  private val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)

  private def findCompanionOf(tpe: String) = {
    val module = runtimeMirror.staticModule(tpe)
    val obj = runtimeMirror.reflectModule(module)
    obj.instance.asInstanceOf[GeneratedMessageCompanion[T] forSome { type T <: GeneratedMessage with Message[T] }]
  }

  val anyWriter: com.google.protobuf.any.Any => JValue = { any =>
    val tpe = any.typeUrl.split("/").lastOption.getOrElse(any.typeUrl)
    val cmp = findCompanionOf(tpe)
    JsonFormat.toJson(any.unpack(cmp)) match {
      case JObject(fields) => JObject(("@type" -> JString(any.typeUrl)) +: fields)
      case value =>
        // Safety net, this shouldn't happen
        throw new IllegalStateException(s"Message of type $tpe emitted non-object JSON $value")
    }
  }

  val anyParser: JValue => com.google.protobuf.any.Any = {
    case obj @ JObject(fields) =>
      obj \ "@type" match {
        case JString(typeUrl) =>
          val cmp = findCompanionOf(typeUrl)
          val tail = fields.filterNot(_._1 == "@type")
          val message = JsonFormat.fromJson(JObject(tail))(cmp)
          com.google.protobuf.any.Any(
            typeUrl = typeUrl,
            value = message.toByteString
          )

        case JNothing =>
          throw new JsonFormatException("Object of type com.google.protobuf.any.Any missing @type field")

        case _ =>
          throw new JsonFormatException(s"Expected string @type field")
      }

    case _ =>
      throw new JsonFormatException("Expected an object")
  }
}
