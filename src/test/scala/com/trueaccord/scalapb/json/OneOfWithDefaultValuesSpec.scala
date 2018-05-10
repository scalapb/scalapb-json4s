package com.trueaccord.scalapb.json

import jsontest.oneof.OneOf
import jsontest.oneof.OneOf._
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.{FlatSpec, MustMatchers}

class OneOfWithDefaultValuesSpec extends FlatSpec with MustMatchers {

  val printer = new Printer(includingDefaultValueFields = true)

  "oneof" should "explude field if was set to empty (default)" in {
    printer.toJson(OneOf(Field.Empty)) must be(parse("{}"))
  }

  "oneof" should "include primitive field with default value" in {
    printer.toJson(OneOf(Field.Primitive(""))) must be(parse("""{"primitive":""}"""))
  }

}
