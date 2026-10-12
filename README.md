Json Formatting for ScalaPB
---------------------------

This little library provides conversion methods between ScalaPB messages
and JSON.

[Documentation is here](http://scalapb.github.io/json.html)

Bugs: https://github.com/scalapb/ScalaPB/issues/

Questions: https://groups.google.com/forum/#!forum/scalapb

### Requirements

The `0.12.x` maintenance line supports Scala 2.12, 2.13, and 3.3 LTS on
Java 8 or newer. It retains ScalaPB 0.11.x, protobuf 3.25.x, and json4s 4.0.x.
Build with JDK 11 or newer; explicit Scala and Java release targets preserve
Java 8 bytecode and API compatibility. CI also runs the compiled test suites
on Java 8 and builds on JDK 11 and 21.

MiMa checks all three Scala artifacts against the published `0.12.3` release.

