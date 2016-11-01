package com.trueaccord.scalapb.json

import org.json4s.JsonAST.JString

object WellKnownTypes {
  // Timestamp for "0001-01-01T00:00:00Z"
  val TIMESTAMP_SECONDS_MIN = -62135596800L

  val TIMESTAMP_SECONDS_MAX = 253402300799L

  val NANOS_PER_SECOND = 1000000000
  val NANOS_PER_MILLISECOND = 1000000
  val NANOS_PER_MICROSECOND = 1000
  val MILLIS_PER_SECOND = 1000
  val MICROS_PER_SECOND = 1000000

  def formatNanos(nanos: Int) = {
    // Determine whether to use 3, 6, or 9 digits for the nano part.
    if (nanos % NANOS_PER_MILLISECOND == 0) {
      "%1$03d".format(nanos / NANOS_PER_MILLISECOND)
    } else if (nanos % NANOS_PER_MICROSECOND == 0) {
      "%1$06d".format(nanos / NANOS_PER_MICROSECOND)
    } else {
      "%1$09d".format(nanos)
    }
  }

  def writeDuration(duration: com.google.protobuf.duration.Duration) = {
    val result = new StringBuilder
    val (seconds, nanos) = if (duration.seconds < 0 || duration.nanos < 0) {
      result.append("-")
      (-duration.seconds, -duration.nanos)
    } else (duration.seconds, duration.nanos)

    result.append(seconds)
    if (nanos != 0) {
      result.append(".")
      result.append(formatNanos(nanos))
    }
    result.append("s")
    JString(result.result())
  }
}
