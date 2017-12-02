package com.trueaccord.scalapb

package object json {
  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  type JsonFormatException = _root_.scalapb.json4s.JsonFormatException

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  type Formatter[T] = _root_.scalapb.json4s.Formatter[T]

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  type FormatRegistry = _root_.scalapb.json4s.FormatRegistry

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  type TypeRegistry = _root_.scalapb.json4s.TypeRegistry

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  val TypeRegistry = _root_.scalapb.json4s.TypeRegistry

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  type Printer = _root_.scalapb.json4s.Printer

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  type Parser = _root_.scalapb.json4s.Parser

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  val JsonFormat = _root_.scalapb.json4s.JsonFormat

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  val NameUtils = _root_.scalapb.json4s.NameUtils

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  val Timestamps = _root_.scalapb.json4s.Timestamps

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  val Durations = _root_.scalapb.json4s.Durations

  @deprecated("Please use scalapb.json4s package instead of com.trueaccord.scalapb.json", "0.7.0")
  val StructFormat = _root_.scalapb.json4s.StructFormat
}
