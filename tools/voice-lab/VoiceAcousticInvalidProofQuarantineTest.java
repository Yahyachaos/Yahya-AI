/** Android-free regression: bad current-epoch speaker proof never leaves a live stale mouth cursor. */
public final class VoiceAcousticInvalidProofQuarantineTest {
    private static int checks;
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
        checks++;
    }
    private static void reject(boolean ok, String reason) { check(!ok, reason); }

    /** A rollback is a poisoned stream, not simply an ignored sensor observation. */
    private static void rollbackPoisonsUntilFreshAudioGeneration() {
        VoiceAcousticTimingContract c = new VoiceAcousticTimingContract();
        c.begin(1, 48000);
        check(c.acceptMeasuredDelay(1, 240), "fixture external delay");
        check(c.observe(1, 1200, 1800, 1_000_000_000L, 1_005_000_000L), "trusted sample");
        check(c.estimatedMouthFrame(1, 1_010_000_000L) == 960, "trusted mouth cursor");
        reject(c.observe(1, 1199, 1800, 1_020_000_000L, 1_025_000_000L), "rollback refused");
        check(c.estimatedMouthFrame(1, 1_030_000_000L) == -1,
                "rejected same-epoch backward hardware head must remove stale mouth cursor");
        reject(c.observe(1, 1500, 1800, 1_040_000_000L, 1_045_000_000L),
                "poisoned generation cannot self-heal from later good report");
        reject(c.acceptMeasuredDelay(1, 240), "poisoned generation cannot recalibrate in-place");
        check(c.estimatedMouthFrame(1, 1_049_000_000L) == -1, "poison stays closed");
        c.begin(2, 48000);
        check(c.acceptMeasuredDelay(2, 240), "fresh epoch can calibrate");
        check(c.observe(2, 2000, 3000, 1_050_000_000L, 1_055_000_000L), "fresh epoch valid");
        check(c.estimatedMouthFrame(2, 1_056_000_000L) == 1760, "new epoch visible");
        reject(c.observe(1, 2100, 3000, 1_057_000_000L, 1_058_000_000L),
                "old epoch never poisons new mouth");
        check(c.estimatedMouthFrame(2, 1_059_000_000L) == 1760, "new cursor unaffected by old callback");
    }

    /** Malformed capture closes UI but can recover against immutable trusted baseline. */
    private static void malformedSampleRequiresFreshMonotoneProof() {
        VoiceAcousticTimingContract c = new VoiceAcousticTimingContract();
        c.begin(3, 48000);
        check(c.acceptMeasuredDelay(3, 100), "measured delay");
        long t=4_000_000_000L;
        check(c.observe(3, 500, 600, t, t+1_000_000L), "initial");
        check(c.estimatedMouthFrame(3, t+2_000_000L)==400, "initial mouth");
        reject(c.observe(3, 550, 540, t+20_000_000L, t+21_000_000L),
                "head ahead of accepted frames rejected");
        check(c.estimatedMouthFrame(3, t+22_000_000L)==-1,
                "temporary bad capture closes mouth immediately");
        reject(c.observe(3, 550, 1000, t, t+22_500_000L),
                "same old capture timestamp cannot move head forward");
        check(c.estimatedMouthFrame(3, t+23_000_000L)==-1,
                "same old capture cannot resurrect cursor");
        check(c.observe(3, 550, 1000, t+24_000_000L, t+25_000_000L),
                "only trusted monotonic recovery accepted");
        check(c.estimatedMouthFrame(3, t+26_000_000L)==450, "recovery yields correct cursor");
        reject(c.observe(3, 550, 1000, t+23_000_000L, t+28_000_000L),
                "rollback of capture time poisons epoch");
        check(c.estimatedMouthFrame(3, t+29_000_000L)==-1,
                "time rollback remains closed");
    }

    /** Repeated synthetically invalid samples must not leak old mouth frames. */
    private static void deterministicRejectStorm() {
        for(int i=0; i<120; i++) {
            VoiceAcousticTimingContract c = new VoiceAcousticTimingContract();
            c.begin(1,48000);
            check(c.acceptMeasuredDelay(1,240), "storm calibration "+i);
            long t=10_000_000_000L+i*1_000_000_000L;
            check(c.observe(1,1000+i,2000+i,t,t+1_000_000L), "storm initial "+i);
            check(c.estimatedMouthFrame(1,t+2_000_000L)==760+i,"storm before " + i);
            if(i%2==0) {
                reject(c.observe(1,1001+i,1000+i,t+10_000_000L,t+11_000_000L),
                        "malformed ahead-of-accepted " +i);
            } else {
                reject(c.observe(1,999+i,2000+i,t+10_000_000L,t+11_000_000L),
                        "backwards head " +i);
            }
            check(c.estimatedMouthFrame(1,t+12_000_000L)==-1,
                    "invalid sample closes mouth " + i);
            c.stop(1);
            check(c.estimatedMouthFrame(1,t+13_000_000L)==-1,"stop keeps closed " + i);
        }
    }
    public static void main(String[] args) {
        rollbackPoisonsUntilFreshAudioGeneration();
        malformedSampleRequiresFreshMonotoneProof();
        deterministicRejectStorm();
        System.out.println("VOICE_ACOUSTIC_INVALID_PROOF_QUARANTINE_PASS checks="+checks);
    }
}
