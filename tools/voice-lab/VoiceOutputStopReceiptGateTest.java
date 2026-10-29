/** Isolated deterministic stop/abort identity race fixture; no Android output. */
public final class VoiceOutputStopReceiptGateTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        checks++;
    }
    private static void reject(boolean value, String reason) { check(!value, reason); }

    private static void basics() {
        var gate = new VoiceOutputStopReceiptGate(500);
        reject(gate.request(0, 1, 100) != null, "invalid plan");
        reject(gate.request(1, 0, 100) != null, "invalid audio epoch");
        reject(gate.request(1, 1, -1) != null, "invalid time");
        var a = gate.request(1, 5, 100);
        check(a != null && a.nonce == 1, "lease nonce");
        reject(gate.request(1, 5, 100) != null, "no concurrent stop lease");
        reject(gate.acknowledgeStopped(a, 2, 5, 150), "wrong plan cannot ack");
        reject(gate.acknowledgeStopped(a, 1, 6, 150), "wrong audio cannot ack");
        reject(gate.consumeForExplicitAbort(a, 1, 5, 150), "no premature abort");
        check(gate.acknowledgeStopped(a, 1, 5, 200), "correct stop claim");
        reject(gate.acknowledgeStopped(a, 1, 5, 220), "duplicate stop claim");
        reject(gate.consumeForExplicitAbort(a, 1, 6, 250), "wrong epoch cannot abort");
        check(gate.consumeForExplicitAbort(a, 1, 5, 250), "one-shot abort");
        reject(gate.consumeForExplicitAbort(a, 1, 5, 251), "no receipt replay");
        var b = gate.request(2, 6, 300);
        check(b != null && b.nonce > a.nonce, "next epoch is distinct");
        reject(gate.acknowledgeStopped(a, 1, 5, 301), "stale ACK cannot affect new lease");
        reject(gate.consumeForExplicitAbort(a, 2, 6, 310), "stale STOP cannot abort successor");
        check(gate.invalidate(b), "cancel pending request");
        reject(gate.acknowledgeStopped(b, 2, 6, 330), "invalidated STOP cannot revive");
        reject(gate.request(1, 6, 350) != null, "old plan rejected");
        reject(gate.request(2, 5, 350) != null, "old audio rejected");
        var c = gate.request(2, 6, 400);
        check(c != null, "retry same epoch with new nonce");
        reject(gate.acknowledgeStopped(b, 2, 6, 420), "old retry receipt rejected");
        check(gate.acknowledgeStopped(c, 2, 6, 430), "valid retry");
        check(gate.consumeForExplicitAbort(c, 2, 6, 440), "retry consumed");
    }

    private static void clockAndExpiry() {
        var gate = new VoiceOutputStopReceiptGate(200);
        var a = gate.request(1, 1, 100);
        reject(gate.acknowledgeStopped(a, 1, 1, 99), "backward ACK poisons lease");
        reject(gate.acknowledgeStopped(a, 1, 1, 101), "poisoned lease cannot heal");
        var b = gate.request(2, 2, 120);
        check(b != null, "fresh plan after poison");
        reject(gate.acknowledgeStopped(b, 2, 2, 321), "expired ACK poisons");
        reject(gate.request(3, 3, 320) != null, "expired ACK must advance observed clock");
        var c = gate.request(3, 3, 400);
        check(gate.acknowledgeStopped(c, 3, 3, 500), "ack inside lease");
        reject(gate.consumeForExplicitAbort(c, 3, 3, 601), "old ACK cannot be consumed late");
        reject(gate.request(4, 4, 600) != null, "expired consume must advance observed clock");
        var d = gate.request(4, 4, 650);
        check(gate.acknowledgeStopped(d, 4, 4, 700), "new ACK");
        reject(gate.consumeForExplicitAbort(d, 4, 4, 699), "time reversal poisons consume");
        reject(gate.consumeForExplicitAbort(d, 4, 4, 701), "cannot heal consumed/poisoned");
        reject(gate.request(5, 5, 649) != null, "global time cannot regress");
        check(gate.request(5, 5, 710) != null, "forward clock permits new request");
    }

    private static void deterministicRaces() {
        var gate = new VoiceOutputStopReceiptGate(4000);
        long now = 1000;
        VoiceOutputStopReceiptGate.StopRequest prior = null;
        for (int i = 1; i <= 256; i++) {
            now += 10;
            var request = gate.request(i, i + 1000, now);
            check(request != null, "new lease " + i);
            if (prior != null) {
                reject(gate.acknowledgeStopped(prior, i - 1, i + 999, now + 1),
                        "late stop from prior generation " + i);
                reject(gate.consumeForExplicitAbort(prior, i, i + 1000, now + 2),
                        "prior receipt cannot carry forward " + i);
            }
            reject(gate.acknowledgeStopped(request, i, i + 1001, now + 3),
                    "wrong output epoch " + i);
            check(gate.acknowledgeStopped(request, i, i + 1000, now + 4),
                    "own output stopped " + i);
            reject(gate.consumeForExplicitAbort(request, i + 1, i + 1000, now + 5),
                    "wrong plan abort " + i);
            check(gate.consumeForExplicitAbort(request, i, i + 1000, now + 6),
                    "exact one-shot consume " + i);
            prior = request;
            now += 6;
        }
    }

    public static void main(String[] args) {
        basics();
        clockAndExpiry();
        deterministicRaces();
        System.out.println("VOICE_OUTPUT_STOP_RECEIPT_GATE_PASS checks=" + checks);
    }
}
