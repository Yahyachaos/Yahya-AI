#!/usr/bin/env sh
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT HUP INT TERM
javac -encoding UTF-8 -d "$TEMP" "$DIR/VoiceLifecycleContract.java" "$DIR/VoiceLifecycleContractTest.java" "$DIR/VoiceLifecycleAdversarialTest.java" "$DIR/VoicePlaybackHeadTracker.java" "$DIR/VoicePlaybackHeadTrackerTest.java" "$DIR/GermanSpeechChunkPlanner.java" "$DIR/GermanSpeechChunkPlannerTest.java" "$DIR/GermanSpeechChunkPlannerRegressionTest.java" "$DIR/VoiceAcousticTimingContract.java" "$DIR/VoiceAcousticTimingContractTest.java"
java -cp "$TEMP" VoiceLifecycleContractTest
java -cp "$TEMP" VoiceLifecycleAdversarialTest
java -cp "$TEMP" VoicePlaybackHeadTrackerTest
java -cp "$TEMP" GermanSpeechChunkPlannerTest
java -cp "$TEMP" GermanSpeechChunkPlannerRegressionTest
java -cp "$TEMP" VoiceAcousticTimingContractTest
