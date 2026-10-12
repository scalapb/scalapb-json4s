# Change log

## Unreleased

- Bound JSON parsing to 100 nested objects/arrays by default, including the root
  container. `Parser.withNestingLimit` configures this limit. Checks run before
  json4s tree construction for string inputs and before protobuf conversion for
  caller-supplied `JValue` trees. They cover Any-of-Any, Struct/Value, repeated
  fields, maps, and ignored fields.
- Reject numeric literals longer than 1024 characters before arbitrary-precision
  conversion. Check unsigned ranges before expanding exponents, and reject enum
  numbers outside the signed 32-bit range instead of truncating them.
- Accept standard and URL-safe Base64 bytes, with or without padding, and report
  malformed Base64 as `JsonFormatException`.
- Preserve `google.protobuf.NullValue` oneof presence when reading JSON `null`.
