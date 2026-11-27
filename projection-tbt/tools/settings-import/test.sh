#!/bin/sh
# Host test of the one-time take-over of the owner's turn-by-turn settings from the Wireless
# mod 2.x (WirelessImport), on a temporary settings folder.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" $(find stubs test -name '*.java') ../../src/hmgapi/mods/tbt/WirelessImport.java
java -cp "$CP" hmgapi.mods.tbt.WirelessImportTest
