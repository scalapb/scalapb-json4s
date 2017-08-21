package com.trueaccord.scalapb.json

import com.google.protobuf.any.{Any => PBAny}
import com.google.protobuf.util.JsonFormat.TypeRegistry
import jsontest.issue315.{Bar, Foo}
import org.scalatest.{FlatSpec, MustMatchers}

class AnyFormatSpec extends FlatSpec with MustMatchers with JavaAssertions {
  val FooExample = Foo("test")

  val AnyExample = PBAny.pack(FooExample)

  val BarExample = Bar("test1", "test2")

  val AnyExample2 = PBAny.pack(BarExample, "example.com/")

  override def javaJsonTypeRegistry =
    TypeRegistry
      .newBuilder()
      .add(Foo.javaDescriptor)
      .add(Bar.javaDescriptor)
      .build

  "Any" should "be serialized the same as in Java (and parsed back to original)" in {
    assertJsonIsSameAsJava(AnyExample)
    assertJsonIsSameAsJava(AnyExample2)
  }
}
