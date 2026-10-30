/** FUTURE-ONLY deterministic JVM test: cumulatively accepted PCM cannot rewind. */
public final class VoiceAcousticAcceptedRollbackTest {
    private static int checks;
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        checks++;
    }

    private static void rollbackMustPoisonCurrentAudioEpoch() {
        for (int round = 1; round <= 256; round++) {
            VoiceAcousticTimingContract clock = new VoiceAcousticTimingContract();
            long epoch = round;
            long ns = 1_000_000_000L + round * 1_000_000_000L;
            check(clock.estimatedMouthFrame(epoch, ns) == -1, "uncalibrated mouth closed");
            clock.begin(epoch, 48000);
            check(clock.acceptMeasuredDelay(epoch, 50), "explicit device-fixture delay");
            check(clock.observe(epoch, 100, 1000 + round, ns, ns + 1_000_000L),
                    "first PCM and head observation");
            check(clock.estimatedMouthFrame(epoch, ns + 1_000_000L) == 50,
                    "calibrated baseline safe");
            // Both observations satisfy head <= accepted. The second still rolls
            // back cumulatively written PCM while the physical head advances.
            check(!clock.observe(epoch, 200, 900 + round,
                            ns + 10_000_000L, ns + 11_000_000L),
                    "cumulative accepted PCM rollback must be rejected");
            check(clock.estimatedMouthFrame(epoch, ns + 11_000_000L) == -1,
                    "invalid same-track accepted provenance closes mouth");
            check(!clock.observe(epoch, 300, 1100 + round,
                            ns + 20_000_000L, ns + 21_000_000L),
                    "real accepted-frame rollback requires new track generation");
            check(!clock.acceptMeasuredDelay(epoch, 10),
                    "poisoned calibration cannot be reopened");
            clock.begin(epoch + 257, 48000);
            check(clock.acceptMeasuredDelay(epoch + 257, 50), "new audio generation recovers");
            check(!clock.observe(epoch, 400, 2000, ns + 25_000_000L, ns + 26_000_000L),
                    "stale old-epoch sample rejected");
            check(clock.observe(epoch + 257, 100, 250,
                            ns + 30_000_000L, ns + 31_000_000L),
                    "fresh track observations remain accepted");
        }
    }

    private static void equalOrIncreasingPcmIsAllowed() {
        for (int round = 1; round <= 256; round++) {
            VoiceAcousticTimingContract clock = new VoiceAcousticTimingContract();
            long ns = round * 1_000_000_000L;
            clock.begin(1, 48000);
            check(clock.acceptMeasuredDelay(1, 0), "zero delay only with explicit fixture");
            check(clock.observe(1, 100, 300, ns, ns + 1_000_000L), "baseline");
            check(clock.observe(1, 150, 300,
                            ns + 10_000_000L, ns + 11_000_000L),
                    "cumulative accepted unchanged is legal");
            check(clock.observe(1, 200, 350,
                            ns + 20_000_000L, ns + 21_000_000L),
                    "additional accepted PCM is legal");
            check(clock.estimatedMouthFrame(1, ns + 21_000_000L) == 200,
                    "valid positive cursor available");
        }
    }

    public static void main(String[] args) {
        rollbackMustPoisonCurrentAudioEpoch();
        equalOrIncreasingPcmIsAllowed();
        System.out.println("VOICE_ACOUSTIC_ACCEPTED_ROLLBACK_PASS checks=" + checks);
    }
}
