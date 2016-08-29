#!/usr/bin/env bash
set -e
~/bin/scalapbc-0.5.39/bin/scalapbc src/test/protobuf/test.proto --scala_out=src/test/scala/
