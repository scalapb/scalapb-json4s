Json Formatting for ScalaPB
---------------------------

This little library provides conversion methods between ScalaPB messages
and JSON.

[Documentation is here](http://scalapb.github.io/json.html)

Bugs: https://github.com/scalapb/ScalaPB/issues/

Questions: https://groups.google.com/forum/#!forum/scalapb


### Requirements

Scala 2.12/2.13 require Java 8 or newer; Scala 3.3 LTS requires Java 11 or
newer. These minimums include the runtime dependencies: [ScalaPB 1.0.0-alpha.6](https://github.com/scalapb/ScalaPB/blob/v1.0.0-alpha.6/project/BuildHelper.scala)
and json4s 4.1.1 target Java 8 for Scala 2 and Java 11 for Scala 3.
[Protobuf Java 4.36.2 still targets Java 8](https://github.com/protocolbuffers/protobuf/blob/v36.2/java/pom.xml).

Build with JDK 11 or newer. Both Scala and Java compilation use explicit release
targets (8 for Scala 2, 11 for Scala 3) to constrain bytecode and JDK API usage.
Validate all three Scala versions on JDK 11 and 21, and the Scala 2 artifacts on
Java 8; a release target alone does not verify runtime compatibility.

MiMa checks master against the published `1.0.0-alpha.2` artifacts for all three
Scala versions. For the `0.12.x` maintenance line, the corresponding last-release
baseline is `0.12.3`; that line retains protobuf 3 and json4s 4.0.x. Compatibility
across dependency major versions is not implied by a successful MiMa check of
this library.

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
