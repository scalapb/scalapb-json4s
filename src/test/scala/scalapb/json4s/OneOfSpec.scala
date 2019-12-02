package scalapb.json4s

import com.google.protobuf.util.JsonFormat.{printer => ProtobufJavaPrinter}
import jsontest.oneof.OneOf._
import jsontest.oneof.Pair.ValueByType._
import jsontest.oneof.{Dictionary, OneOf, OneOfMessage, Pair}
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.prop._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class OneOfSpec
    extends AnyFlatSpec
    with Matchers
    with TableDrivenPropertyChecks {

  val examples = Table(
    ("message", "json"),
    (OneOf.defaultInstance, "{}"),
    (OneOf(Field.Empty), "{}"),
    (OneOf(Field.Primitive("")), """{"primitive":""}"""),
    (OneOf(Field.Primitive("test")), """{"primitive":"test"}"""),
    (OneOf(Field.Wrapper("")), """{"wrapper":""}"""),
    (OneOf(Field.Wrapper("test")), """{"wrapper":"test"}"""),
    (OneOf(Field.Message(OneOfMessage())), """{"message":{}}"""),
    (
      OneOf(Field.Message(OneOfMessage(Some("test")))),
      """{"message":{"field":"test"}}"""
    )
  )

  forEvery(examples) { (message: OneOf, json: String) =>
    new Printer().toJson(message) must be(
      parse(json)
    )
    new Printer().toJson(message) must be(
      parse(
        ProtobufJavaPrinter().print(toJavaProto(message))
      )
    )

    new Printer().includingDefaultValueFields.toJson(message) must be(
      parse(json)
    )
    new Printer().includingDefaultValueFields.toJson(message) must be(
      parse(
        ProtobufJavaPrinter()
          .includingDefaultValueFields()
          .print(toJavaProto(message))
      )
    )
  }

  "dictionary test" should "preserve zero values in one of" in {
    val message = Dictionary(Seq(Pair("myKey", Uint32Value(0))))

    new Printer().toJson(message) must be(
      parse("""{"pairs":[{"key": "myKey", "uint32Value": 0}]}""")
    )

    new Printer().includingDefaultValueFields.toJson(message) must be(
      parse("""{"pairs":[{"key": "myKey", "uint32Value": 0}]}""")
    )
  }
}
