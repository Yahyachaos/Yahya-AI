/** Independent positive and negative evidence fixtures; NO genuine speaker capture. */
public final class VoiceVerifiedPresentationClockTest {
    private static int checks;
    private static void ok(boolean b, String why) {
        if (!b) throw new AssertionError("FAIL " + why);
        checks++;
    }
    private static void eq(long expected, long actual, String why) {
        ok(expected == actual, why + " expected=" + expected + " actual=" + actual);
    }
    public static void main(String[] args) {
        VoiceVerifiedPresentationClock c = new VoiceVerifiedPresentationClock();
        long id=1L;
        c.begin(id, 48000);
        long t=1_000_000_000L;
        eq(-1, c.presentedFrame(id,t), "unmeasured default refuses to fake mouth frames");
        ok(!c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.RAW_HEAD_ONLY,
                380,480,480,t,t+1_000_000L), "raw head alone cannot certify speaker frames");
        eq(-1, c.presentedFrame(id,t+2_000_000L), "no evidence after raw-only attempt");
        ok(c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                380,480,480,t,t+1_000_000L), "externally captured frame 380 accepted");
        eq(380, c.presentedFrame(id,t+2_000_000L), "only supplied external sample is visible");
        eq(380, c.presentedFrame(id,t+90_000_000L), "waiting does not extrapolate sample");
        eq(-1, c.presentedFrame(id,t+202_000_000L), "stale capture expires");
        ok(!c.observe(id+4, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                420,480,480,t+100_000_000L,t+101_000_000L), "old/wrong epoch cannot mutate stream");
        eq(380, c.presentedFrame(id,t+100_000_000L), "wrong-epoch proof cannot overwrite current one");
        ok(c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                420,480,480,t+100_000_000L,t+101_000_000L), "new actual captured final tail position");
        eq(420, c.presentedFrame(id,t+102_000_000L), "explicit speaker evidence advances despite fixed raw head");
        ok(c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,480,480,t+180_000_000L,t+181_000_000L), "confirmed final captured speaker sample");
        eq(480, c.presentedFrame(id,t+182_000_000L), "completed presentation only with external proof");
        ok(!c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                481,480,480,t+190_000_000L,t+191_000_000L), "cannot present unwritten or unplayed frame");
        eq(-1, c.presentedFrame(id,t+192_000_000L), "invalid current proof clears earlier display confidence");
        ok(c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,480,480,t+195_000_000L,t+196_000_000L), "fresh valid external evidence can recover");
        ok(!c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                470,480,480,t+198_000_000L,t+199_000_000L), "speaker capture backwards poisons epoch");
        eq(-1, c.presentedFrame(id,t+200_000_000L), "poisoned epoch fails closed");
        ok(!c.observe(id, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,480,480,t+205_000_000L,t+206_000_000L), "poisoned epoch cannot be reused");
        c.stop(id);
        eq(-1, c.presentedFrame(id,t+207_000_000L), "stopped epoch silent");

        id=2;
        c.begin(id,48000);
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                30,40,40,t+220_000_000L,t+220_000_000L+201_000_000L), "stale capture not accepted");
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                30,40,40,t+221_000_000L,t+220_000_000L), "future capture not accepted");
        ok(c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                30,40,40,t+222_000_000L,t+223_000_000L), "fresh capture after invalid input");
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                31,40,40,t+222_000_000L,t+223_000_000L), "advanced frame on identical timestamp rejected");
        eq(-1,c.presentedFrame(id,t+224_000_000L),"rejected timestamp cannot remain trusted");
        ok(c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                35,40,40,t+225_000_000L,t+226_000_000L), "fresh timestamp recovers known clock");
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                38,40,40,t+224_000_000L,t+227_000_000L), "clock rollback is fatal for same epoch");
        eq(-1,c.presentedFrame(id,t+228_000_000L),"rollback remains silent");

        id=3;
        c.begin(id,48000);
        ok(c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                10,20,20,t+300_000_000L,t+301_000_000L), "fresh third generation capture");
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                10000,10000,10000,t+301_000_000L,t+302_000_000L), "implausible frame-speed leap rejected");
        eq(-1,c.presentedFrame(id,t+303_000_000L),"implausible sample invalidates confidence");
        c.stop(id);
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                15,20,20,t+320_000_000L,t+321_000_000L), "stop refuses new observations");
        eq(-1,c.presentedFrame(id,t+321_000_000L),"stopped cursor stays silent");
        id=4;
        c.begin(id,48000);
        ok(c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                100,120,120,t+400_000_000L,t+401_000_000L), "fourth generation baseline");
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.RAW_HEAD_ONLY,
                101,120,120,t+402_000_000L,t+403_000_000L), "invalid proof clears visible evidence");
        eq(-1,c.presentedFrame(id,t+404_000_000L),"clear after invalid kind");
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                99,120,120,t+405_000_000L,t+406_000_000L), "rollback after rejected proof still poisoned");
        ok(!c.observe(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                101,120,120,t+407_000_000L,t+408_000_000L), "once poisoned cannot recover within epoch");
        eq(-1,c.presentedFrame(id,t+409_000_000L),"poisoned after transient invalid proof");
        System.out.println("VOICE_VERIFIED_PRESENTATION_CLOCK_PASS checks="+checks);
    }
}
