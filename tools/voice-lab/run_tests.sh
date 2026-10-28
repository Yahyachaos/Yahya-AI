#!/usr/bin/env sh
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT HUP INT TERM
javac -encoding UTF-8 -d "$TEMP" "$DIR/VoiceLifecycleContract.java" "$DIR/VoiceLifecycleContractTest.java"
java -cp "$TEMP" VoiceLifecycleContractTest
