import java.util.Random;

/** Synthetic capture claims only. This MUST NOT be described as S25 hardware proof. */
public final class GermanSpeakerTailProofGateTest {
    private static int checks;
    private static void ok(boolean b, String why) {
        if (!b) throw new AssertionError("FAIL " + why);
        checks++;
    }
    private static void no(boolean b, String why) { ok(!b, why); }
    public static void main(String[] args) {
        GermanSpeakerTailProofGate g = new GermanSpeakerTailProofGate();
        long t = 1_000_000_000L;
        g.begin(1,48000);
        no(g.permitNextChunk(1,t), "no raw drain or speaker proof");
        no(g.markRawDrained(1,480,479), "partial raw drain not accepted");
        ok(g.markRawDrained(1,480,480), "exact raw drain registered");
        no(g.permitNextChunk(1,t), "raw head alone is never acoustic proof");
        no(g.observeCapturedTail(1, VoiceVerifiedPresentationClock.EvidenceKind.RAW_HEAD_ONLY,
                480,480,480,t,t+1_000_000L), "raw-only evidence rejected");
        no(g.permitNextChunk(1,t+2_000_000L), "raw-only fails closed");
        ok(g.observeCapturedTail(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                420,480,480,t+3_000_000L,t+4_000_000L), "partial speaker tail claim admitted");
        no(g.permitNextChunk(1,t+5_000_000L), "partial tail does not permit next speech");
        no(g.observeCapturedTail(99, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,480,480,t+6_000_000L,t+7_000_000L), "wrong epoch rejected");
        no(g.observeCapturedTail(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,481,481,t+6_000_000L,t+7_000_000L), "wrong accepted/raw count rejected");
        no(g.permitNextChunk(1,t+201_000_001L), "stale partial proof expires");
        ok(g.observeCapturedTail(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,480,480,t+100_000_000L,t+101_000_000L), "actual full external claim");
        no(g.permitNextChunk(1,t+302_000_000L), "full but expired proof not enough");
        no(g.permitNextChunk(1,t+102_000_000L), "backdated clock cannot revive expired proof");
        ok(g.observeCapturedTail(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,480,480,t+320_000_000L,t+321_000_000L), "new capture after expiry");
        ok(g.permitNextChunk(1,t+322_000_000L), "new fresh captured tail permits once");
        no(g.permitNextChunk(1,t+323_000_000L), "no double permit");
        no(g.observeCapturedTail(1, VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                480,480,480,t+104_000_000L,t+105_000_000L), "used proof cannot mutate old permit");

        g.begin(2,48000);
        ok(g.markRawDrained(2,120,120), "second raw epoch");
        ok(g.observeCapturedTail(2,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                120,120,120,t+200_000_000L,t+201_000_000L), "second full captured tail");
        no(g.observeCapturedTail(2,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                119,120,120,t+220_000_000L,t+221_000_000L), "speaker clock rollback rejected");
        no(g.permitNextChunk(2,t+222_000_000L), "poisoned epoch cannot certify next chunk");
        g.stop(2);
        no(g.permitNextChunk(2,t+223_000_000L), "stopped epoch cannot release next chunk");

        g.begin(3,48000);
        no(g.markRawDrained(2,80,80), "stale raw drain cannot bind new epoch");
        ok(g.markRawDrained(3,80,80), "new raw drain");
        no(g.observeCapturedTail(3,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                80,80,80,t+300_000_000L,t+501_000_000L), "expired capture event rejected");
        no(g.permitNextChunk(3,t+502_000_000L), "never accept expired evidence");
        g.stop(3);

        Random rnd = new Random(0xC311C0DEL);
        for (long id=4;id<104;id++) {
            g.begin(id,48000);
            long total=16+rnd.nextInt(1900);
            long base=t+id*1_000_000_000L;
            ok(g.markRawDrained(id,total,total), "seeded raw drain "+id);
            no(g.permitNextChunk(id,base), "seeded no-capture fail closed "+id);
            long partial=total-rnd.nextInt((int)total)-1;
            ok(g.observeCapturedTail(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    partial,total,total,base,base+1_000_000L), "seeded partial capture "+id);
            no(g.permitNextChunk(id,base+2_000_000L), "seeded partial no-permit "+id);
            ok(g.observeCapturedTail(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    total,total,total,base+100_000_000L,base+101_000_000L), "seeded full capture "+id);
            no(g.permitNextChunk(id,base+302_000_000L), "seeded expired no-permit "+id);
            no(g.permitNextChunk(id,base+102_000_000L), "seeded backdated clock rejected "+id);
            ok(g.observeCapturedTail(id,VoiceVerifiedPresentationClock.EvidenceKind.EXTERNAL_CAPTURE,
                    total,total,total,base+320_000_000L,base+321_000_000L), "seeded fresh recapture "+id);
            ok(g.permitNextChunk(id,base+322_000_000L), "seeded permitted after actual claim "+id);
            no(g.permitNextChunk(id,base+323_000_000L), "seeded single-use "+id);
        }
        System.out.println("VOICE_GERMAN_SPEAKER_TAIL_GATE_PASS checks="+checks);
    }
}
