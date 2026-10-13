# Change log

This file tracks all release lines. Master's history below covers changes since
its last common commit with `0.12.x`, `2afe38f` (2023-11-23).

## Unreleased — 1.0.x (master)

No changes yet since `1.0.0-alpha.2`.

## Unreleased — 0.12.x

No changes yet since `0.12.3`.

## 1.0.0-alpha.2 — 2026-10-12

Changes since `1.0.0-alpha.1` on the master branch.

### Dependencies and platform support

- Update ScalaPB from `1.0.0-alpha.1` to `1.0.0-alpha.6` and protobuf-java
  from `3.25.5` to `4.36.2`.
- Update json4s from `4.0.7` to `4.1.1`. Its Maven group changes from
  `org.json4s` to `io.github.json4s`; update direct dependency declarations
  and overrides accordingly to avoid mixing the two dependency lines.
- Update the Scala cross-build versions to `2.12.21`, `2.13.18`, and `3.3.8`.
- Require Java 11 or newer for Scala 3. Scala 2.12/2.13 retain Java 8 support.
  Building the project requires JDK 11 or newer.
- Check binary compatibility against `1.0.0-alpha.1`. This does not guarantee
  compatibility across the ScalaPB, protobuf, or json4s dependency upgrades;
  validate downstream applications when upgrading.

### Parsing changes

- Bound JSON parsing to 100 nested objects/arrays by default, including the root
  container. `Parser.withNestingLimit` configures this limit. Checks run before
  json4s tree construction for string inputs and before protobuf conversion for
  caller-supplied `JValue` trees. They cover Any-of-Any, Struct/Value, repeated
  fields, maps, and ignored fields. Previously accepted deeper inputs now fail;
  use `new Parser().withNestingLimit(200)` when deeper trusted input is required.
- Reject numeric literals longer than 1024 characters before arbitrary-precision
  conversion. Numeric strings are checked when converted to protobuf numbers.
  This limit is not configurable through `withNestingLimit`; shorten oversized
  numeric representations before parsing.
- Check unsigned ranges before expanding exponents, avoiding expensive integer
  conversion for out-of-range values such as `1e100000000`.
- Treat enum integers outside the signed 32-bit range as invalid instead of
  truncating them into potentially valid values. Proto3 parsing with
  `ignoringUnknownFields` retains its fallback to the zero enum value.
- Accept standard and URL-safe Base64 bytes, with or without padding, and report
  malformed Base64 as `JsonFormatException`. The new decoder rejects whitespace
  in Base64 strings that the previous decoder could accept; remove that whitespace
  before parsing.
- Preserve `google.protobuf.NullValue` oneof presence when reading JSON `null`.
  Supplying another member of the same oneof now triggers overlap validation
  instead of silently ignoring the null-valued member.

### Build and release validation

- Validate all Scala builds on JDK 11 and 21, and Scala 2 artifacts on Java 8.
  Explicit Java release targets constrain bytecode and JDK API usage.
- Run tests, formatting, and binary-compatibility checks on tag revisions before
  publishing. Modernize publishing metadata and release tooling.

## 0.12.3 — 2026-10-12

Changes since `0.12.2` on the `0.12.x` maintenance branch.

### Parsing fixes

- Check unsigned integer ranges before expanding exponents, avoiding expensive
  integer conversion for out-of-range values such as `1e100000000`. Valid unsigned
  boundaries and integral decimal/exponent forms remain supported.
- Treat enum integers outside the signed 32-bit range as invalid instead of
  truncating them into potentially valid values. Proto3 parsing with
  `ignoringUnknownFields` retains its fallback to the zero enum value.
- Preserve `google.protobuf.NullValue` oneof presence when reading JSON `null`.
  Supplying another member of the same oneof now triggers overlap validation
  instead of silently ignoring the null-valued member.
- Accept standard and URL-safe Base64 bytes, with or without padding. Preserve
  all forms accepted by the legacy decoder, including its whitespace handling.
  Malformed Base64 now raises `JsonFormatException` rather than leaking the
  decoder's `IllegalArgumentException`.
- Preserve existing parsing defaults: this branch does not introduce master's
  100-container nesting limit or 1024-character numeric-literal limit.

### Dependencies and platform support

- Update ScalaPB from `0.11.18` to `0.11.21`, protobuf-java from `3.25.8` to
  `3.25.9`, and json4s from `4.0.6` to `4.0.7`.
- Retain the ScalaPB 0.11.x, protobuf 3.x, and json4s 4.0.x dependency lines.
- Update the Scala cross-build versions to `2.12.21`, `2.13.18`, and `3.3.8`.
- Retain Java 8 runtime support for all Scala builds. Building the project now
  requires JDK 11 or newer, with explicit Java 8 bytecode and API targets.

### Build and release validation

- Validate all Scala builds on Java 8, 11, and 21, and check binary compatibility
  against `0.12.2`.
- Run tests, formatting, and binary-compatibility checks on tag revisions before
  publishing. Modernize publishing metadata and release tooling.

## 1.0.0-alpha.1 — 2024-09-30

Changes on master since the divergence from `0.12.x` at `2afe38f`.
The release tag explicitly states: "Do not use. For adjustment with ScalaPB
1.0.0-alpha.1"; this was an experimental integration release.

### Dependencies and platform support

- Move the ScalaPB compiler and runtime from `0.11.14` to `1.0.0-alpha.1`,
  starting the 1.0 prerelease line. Applications remaining on ScalaPB 0.11.x
  should use the `0.12.x` maintenance line.
- Update protobuf-java from `3.25.1` to `3.25.5` and json4s from `4.0.6` to
  `4.0.7`.
- Update Scala 2.12 from `2.12.18` to `2.12.20` and Scala 2.13 from `2.13.12`
  to `2.13.15`. After interim Scala 3.4 updates, settle on Scala `3.3.4` LTS
  for the release, replacing the divergence point's `3.3.1` build.

### Build tooling

- Update sbt from `1.9.7` to `1.10.2`, sbt-protoc to `1.0.7`, and the test,
  formatting, binary-compatibility, and release tooling.
- No library source changes were made between the divergence point and this
  tag; changes in this interval were limited to dependencies and build settings.
