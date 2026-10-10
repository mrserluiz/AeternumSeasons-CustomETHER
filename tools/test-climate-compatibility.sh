#!/usr/bin/env bash
set -euo pipefail
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
javac -d "$output" Kinkin/aeternum/calendar/WorldClimateProfile.java Kinkin/aeternum/world/BiomePaletteResolver.java tests/ClimateCompatibilityTest.java
java -cp "$output" ClimateCompatibilityTest
