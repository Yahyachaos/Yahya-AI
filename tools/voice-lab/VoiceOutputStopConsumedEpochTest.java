/**
 * FUTURE ONLY: a successfully consumed STOP receipt must terminalize its
 * plan/audio epoch. No subsequent nonce may reauthorize that same epoch.
 * This is synthetic Java evidence, NOT proof of actual Android output silence.
 */
public final class VoiceOutputStopConsumedEpochTest {
    private static long checks;
    private static void expect(boolean actual, String reason) {
        if (!actual) throw new AssertionError(reason);
        checks++;
    }

    public static void main(String[] args) {
        for (int r = 0; r < 256; r++) {
            long p = 10L + 3L * r;
            long a = 1000L + 5L * r;
            long t = 1_000L + 100L * r;
            var gate = new VoiceOutputStopReceiptGate(50);
            var first = gate.request(p, a, t);
            expect(first != null, "initial stop request " + r);
            expect(gate.acknowledgeStopped(first, p, a, t + 1), "external stop claim " + r);
            expect(gate.consumeForExplicitAbort(first, p, a, t + 2), "one-shot consume " + r);
            expect(!gate.hasPendingStop(), "consumption clears pending lease " + r);
            expect(gate.request(p, a, t + 3) == null,
                    "consumed plan/audio epoch must never obtain a new nonce " + r);
            expect(!gate.acknowledgeStopped(first, p, a, t + 4),
                    "old receipt cannot re-arm " + r);
            var successor = gate.request(p + 1, a + 1, t + 5);
            expect(successor != null && successor.nonce > first.nonce,
                    "fresh plan and audio generation must remain valid " + r);
            expect(gate.acknowledgeStopped(successor, p + 1, a + 1, t + 6),
                    "fresh successor stop claim " + r);
            expect(gate.consumeForExplicitAbort(successor, p + 1, a + 1, t + 7),
                    "fresh successor consume " + r);
            expect(gate.request(p + 1, a + 1, t + 8) == null,
                    "successor consumed epoch also terminal " + r);
        }
        {
            var gate = new VoiceOutputStopReceiptGate(50);
            var first = gate.request(1, 1, 100);
            expect(first != null && gate.invalidate(first), "unconsumed cancellation");
            var retry = gate.request(1, 1, 101);
            expect(retry != null && retry.nonce > first.nonce,
                    "unconsumed same-epoch cancellation may retry");
            expect(gate.acknowledgeStopped(retry, 1, 1, 102), "retry ACK");
            expect(gate.consumeForExplicitAbort(retry, 1, 1, 103), "retry terminal consume");
            expect(gate.request(1, 1, 104) == null, "terminal retry cannot repeat");
        }
        {
            var gate = new VoiceOutputStopReceiptGate(10);
            var first = gate.request(4, 9, 100);
            expect(first != null, "expiry original request");
            expect(!gate.acknowledgeStopped(first, 4, 9, 111), "expired ack denied");
            var retry = gate.request(4, 9, 112);
            expect(retry != null, "expired unconsumed epoch may be retried");
            expect(gate.invalidate(retry), "expired retry cleanup");
        }
        System.out.println("VOICE_OUTPUT_STOP_CONSUMED_EPOCH_PASS checks=" + checks);
    }
}
