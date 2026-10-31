import java.util.Random;
/**
 * FUTURE-ONLY local STOP receipt admission ordering demonstration.
 * Uses byte-verified GitHub VoiceOutputStopReceiptGate, but NOT original complete
 * GermanStreamingSpeakerGatedSession or Android runtime.
 */
public final class VoiceAbortReceiptAdmissibilityTest {
    private static long checks;
    private static void check(boolean yes, String label) {
        checks++;
        if (!yes) throw new AssertionError(label + " check=" + checks);
    }
    /** Mirrors existing source ordering: consume STOP first, then handle new text. */
    private static boolean oldOrder(VoiceOutputStopReceiptGate gate,
            VoiceOutputStopReceiptGate.StopRequest r, String text, long t) {
        return gate.consumeForExplicitAbort(r, 1, 1, t);
    }
    /** Proposed ordering: admit meaningful text BEFORE any one-shot STOP consume. */
    private static boolean guardedOrder(VoiceOutputStopReceiptGate gate,
            VoiceOutputStopReceiptGate.StopRequest r, String text, long t) {
        return VoiceAbortNewTextGate.hasNonInvisibleContent(text)
                && gate.consumeForExplicitAbort(r, 1, 1, t);
    }
    private static VoiceOutputStopReceiptGate.StopRequest ready(
            VoiceOutputStopReceiptGate gate, long base) {
        var r = gate.request(1, 1, base);
        check(r != null, "request");
        check(gate.acknowledgeStopped(r, 1, 1, base + 10), "ACK");
        return r;
    }
    private static void tested(boolean useGuard) {
        VoiceOutputStopReceiptGate g = new VoiceOutputStopReceiptGate(500_000_000L);
        long base = 1_000_000_000L;
        var r = ready(g, base);
        boolean consumed = useGuard
                ? guardedOrder(g, r, null, base + 20)
                : oldOrder(g, r, null, base + 20);
        check(!consumed, "null text cannot consume one-shot acknowledged STOP");
        check(g.hasPendingStop(), "invalid replacement preserves pending STOP claim");
        check(guardedOrder(g, r, "   \u200b\ufeff ", base + 30) == false,
                "invisible-only text cannot consume STOP");
        check(g.hasPendingStop(), "pending STOP retained after invisible-only text");
        check(guardedOrder(g, r, "Neuer Satz.", base + 40), "valid text may consume once");
        check(!g.hasPendingStop(), "valid consumption clears exactly once");
        check(!guardedOrder(g, r, "Zweiter.", base + 50), "never consume old ACK twice");
        check(g.request(1, 1, base + 60) == null, "terminal consumed epoch cannot rearm");
    }
    private static void seeded() {
        int[] hidden = {' ', '\t', '\n', '\r', 0x00a0, 0x2003, 0x202f, 0x200b, 0x200d, 0xfeff, 0x2060, 0};
        Random random = new Random(0xA800AB0A5EL);
        for (int i = 0; i < 3000; i++) {
            var gate = new VoiceOutputStopReceiptGate(500_000_000L);
            long base = 1_000_000_000L + i * 1_000_000L;
            var req = ready(gate, base);
            StringBuilder invisible = new StringBuilder();
            int len = 1 + random.nextInt(80);
            for (int p = 0; p < len; p++) invisible.appendCodePoint(hidden[random.nextInt(hidden.length)]);
            check(!guardedOrder(gate, req, invisible.toString(), base + 15), "seeded invisible abort reject " + i);
            check(gate.hasPendingStop(), "seeded STOP preserved " + i);
            check(guardedOrder(gate, req, "Danach spreche ich.", base + 20), "seeded valid successor " + i);
            check(!guardedOrder(gate, req, "Doppelt.", base + 30), "seeded duplicate consumption " + i);
            check(gate.request(1, 1, base + 40) == null, "seeded consumed identity terminal " + i);
        }
    }
    public static void main(String[] args) {
        tested(args.length == 0 || !args[0].equals("old"));
        seeded();
        System.out.println("VOICE_STOP_ABORT_ADMISSION_ORDER_PASS checks=" + checks);
    }
}
