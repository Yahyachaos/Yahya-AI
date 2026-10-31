#!/usr/bin/env sh
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT HUP INT TERM
javac -encoding UTF-8 -d "$TEMP" "$DIR/VoiceLifecycleContract.java" "$DIR/VoiceLifecycleContractTest.java" "$DIR/VoiceLifecycleAdversarialTest.java" "$DIR/VoicePlaybackHeadTracker.java" "$DIR/VoicePlaybackHeadTrackerTest.java" "$DIR/VoicePlaybackHeadSignedWrapFuzzTest.java" "$DIR/VoicePlaybackHeadEpochReuseTest.java" "$DIR/GermanSpeechChunkPlanner.java" "$DIR/GermanSpeechChunkPlannerTest.java" "$DIR/GermanSpeechChunkPlannerRegressionTest.java" "$DIR/GermanPlaceAbbreviationTest.java" "$DIR/GermanOrdinalPhraseRegressionTest.java" "$DIR/GermanNumericDateEndTest.java" "$DIR/GermanTerminalAbbreviationTest.java" "$DIR/GermanQuantityScalePhraseTest.java" "$DIR/GermanStreamingDeliveryLedger.java" "$DIR/GermanStreamingDeliveryLedgerTest.java" "$DIR/GermanStreamingRawDrainCoordinator.java" "$DIR/GermanStreamingRawDrainCoordinatorTest.java" "$DIR/GermanSpeakerTailProofGate.java" "$DIR/GermanSpeakerTailProofGateTest.java" "$DIR/GermanStreamingSpeakerGatedSession.java" "$DIR/GermanStreamingSpeakerGatedSessionTest.java" "$DIR/GermanStreamingSpeakerGatedReplayTest.java" "$DIR/VoiceExplicitAbortGate.java" "$DIR/VoiceExplicitAbortGateTest.java" "$DIR/GermanStreamingExplicitAbortIntegrationTest.java" "$DIR/VoiceAcousticTimingContract.java" "$DIR/VoiceAcousticTimingContractTest.java" "$DIR/VoiceAcousticAcceptedRollbackTest.java" "$DIR/VoiceAcousticInvalidProofQuarantineTest.java" "$DIR/VoiceAcousticStopRearmTest.java" "$DIR/VoiceAcousticEpochReuseTest.java" "$DIR/VoiceCrossContractPlaybackTest.java" "$DIR/VoiceCalibratedOracleTest.java" "$DIR/VoiceFinalSyllableTailTest.java" "$DIR/VoiceTailAcousticCeilingTest.java" "$DIR/VoiceVerifiedPresentationClock.java" "$DIR/VoiceVerifiedPresentationClockTest.java" "$DIR/VoiceCaptureFreshnessRegressionTest.java" "$DIR/VoiceCaptureInvalidClockRollbackTest.java" "$DIR/VoiceVerifiedPresentationReplayTest.java" "$DIR/VoiceOutputStopReceiptGate.java" "$DIR/VoiceOutputStopReceiptGateTest.java" "$DIR/GermanStreamingStopReceiptIntegrationTest.java" "$DIR/VoiceOutputStopDuplicateClockTest.java" "$DIR/VoiceCaptureSameTimestampQuarantineTest.java" "$DIR/VoiceCaptureSameClockMutationTest.java" "$DIR/VoiceOutputStopConsumedEpochTest.java" "$DIR/VoiceAbortNewTextGate.java" "$DIR/VoiceAbortNewTextGateTest.java" "$DIR/VoiceAbortReceiptAdmissibilityTest.java"
java -cp "$TEMP" VoiceLifecycleContractTest
java -cp "$TEMP" VoiceLifecycleAdversarialTest
java -cp "$TEMP" VoicePlaybackHeadTrackerTest
java -cp "$TEMP" VoicePlaybackHeadSignedWrapFuzzTest
java -cp "$TEMP" VoicePlaybackHeadEpochReuseTest
java -cp "$TEMP" GermanSpeechChunkPlannerTest
java -cp "$TEMP" GermanSpeechChunkPlannerRegressionTest
java -cp "$TEMP" GermanPlaceAbbreviationTest
java -cp "$TEMP" GermanOrdinalPhraseRegressionTest
java -cp "$TEMP" GermanNumericDateEndTest
java -cp "$TEMP" GermanTerminalAbbreviationTest
java -cp "$TEMP" GermanQuantityScalePhraseTest
java -cp "$TEMP" VoiceAcousticTimingContractTest
java -cp "$TEMP" VoiceAcousticAcceptedRollbackTest
java -cp "$TEMP" VoiceAcousticInvalidProofQuarantineTest
java -cp "$TEMP" VoiceAcousticStopRearmTest
java -cp "$TEMP" VoiceAcousticEpochReuseTest
java -cp "$TEMP" VoiceCrossContractPlaybackTest
java -cp "$TEMP" VoiceCalibratedOracleTest
java -cp "$TEMP" VoiceFinalSyllableTailTest
java -cp "$TEMP" VoiceTailAcousticCeilingTest
java -cp "$TEMP" VoiceVerifiedPresentationClockTest
java -cp "$TEMP" VoiceCaptureFreshnessRegressionTest
java -cp "$TEMP" VoiceCaptureInvalidClockRollbackTest
java -cp "$TEMP" VoiceVerifiedPresentationReplayTest
java -cp "$TEMP" GermanStreamingDeliveryLedgerTest
java -cp "$TEMP" GermanStreamingRawDrainCoordinatorTest
java -cp "$TEMP" GermanSpeakerTailProofGateTest
java -cp "$TEMP" GermanStreamingSpeakerGatedSessionTest
java -cp "$TEMP" GermanStreamingSpeakerGatedReplayTest
java -cp "$TEMP" VoiceExplicitAbortGateTest
java -cp "$TEMP" GermanStreamingExplicitAbortIntegrationTest

java -cp "$TEMP" VoiceOutputStopReceiptGateTest
java -cp "$TEMP" GermanStreamingStopReceiptIntegrationTest
java -cp "$TEMP" VoiceOutputStopDuplicateClockTest
java -cp "$TEMP" VoiceCaptureSameTimestampQuarantineTest
java -cp "$TEMP" VoiceCaptureSameClockMutationTest
java -cp "$TEMP" VoiceOutputStopConsumedEpochTest
java -cp "$TEMP" VoiceAbortNewTextGateTest
java -cp "$TEMP" VoiceAbortReceiptAdmissibilityTest
