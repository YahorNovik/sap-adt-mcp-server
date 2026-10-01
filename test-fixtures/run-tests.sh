#!/usr/bin/env bash
# Compiles the plugin's non-UI classes (parsers + tools) together with the
# fixture test and runs it. No Eclipse/OSGi needed: these classes only use JDK + Gson.
set -e
cd "$(dirname "$0")/.."
SRC=com.sap.adt.mcp.server.plugin/src/com/sap/adt/mcp
GSON=com.sap.adt.mcp.server.plugin/lib/gson-2.11.0.jar
OUT=target-test
rm -rf "$OUT"
javac -nowarn --release 17 -cp "$GSON" -d "$OUT" $SRC/sap/*.java $SRC/tools/*.java test-fixtures/ParserFixtureTest.java
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
java -cp "$OUT$SEP$GSON" ParserFixtureTest test-fixtures
