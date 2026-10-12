Json Formatting for ScalaPB
---------------------------

This little library provides conversion methods between ScalaPB messages
and JSON.

[Documentation is here](http://scalapb.github.io/json.html)

Bugs: https://github.com/scalapb/ScalaPB/issues/

Questions: https://groups.google.com/forum/#!forum/scalapb


### Requirements

The Scala 3 build requires Java 11 or newer with ScalaPB 1.0.0-alpha.6.
Scala 3.3 LTS and Scala 2.12/2.13 remain supported.

### Parsing limits

The parser accepts up to 100 nested JSON objects and arrays by default. The root
container counts as one level; sibling elements do not accumulate depth. This
structural limit applies to ordinary messages, maps, repeated fields, `Any`,
`Struct`/`Value`, and ignored fields. Configure it with
`new Parser().withNestingLimit(200)` when deeper input is trusted. Increasing the
limit also increases the stack and memory required by conversion. A limit of zero
allows only scalar JSON inputs (for example, primitive wrappers).

String inputs are checked with a streaming reader before json4s builds a tree.
`fromJson` checks caller-supplied trees iteratively before converting them. Numeric
literals are limited to 1024 characters; numeric JSON strings are checked when
converted to a protobuf number. These limits do not bound total input size,
container width, string/bytes payload size, or allocations already made by a caller
constructing a `JValue`. A custom formatter that recurses independently must manage
its own recursion. An application's Jackson version may impose additional limits.

Application-defined enum names can be implemented with
`FormatRegistry.registerEnumFormatter`. Automatic support for protobuf's
`(pb.enumvalue.json).string` option requires Edition 2026 support and generated
`google/protobuf/json_enumvalue_options.proto` descriptors; the current ScalaPB
compiler dependency does not support that edition. This is separate from ordinary
field `json_name` options, which are already supported.
