/** FUTURE-ONLY contract: a consumed plan cannot rearm by inventing a new audio epoch. */
public final class VoiceOutputStopConsumedPlanLineageTest {
    private static int checks;
    private static void yes(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label + " check=" + checks);
    }
    private static void seed(long plan, long audio, long base) {
        VoiceOutputStopReceiptGate gate = new VoiceOutputStopReceiptGate(500_000_000L);
        var initial = gate.request(plan, audio, base);
        yes(initial != null, "initial old-plan STOP request");
        yes(gate.acknowledgeStopped(initial, plan, audio, base+1), "old audio stopped claim");
        yes(gate.consumeForExplicitAbort(initial, plan, audio, base+2), "consumed old plan");
        // The abandoned plan is terminal even if a higher audio generation is fabricated.
        yes(gate.request(plan, audio+1, base+3) == null,
                "consumed plan may not acquire fresh nonce for fabricated later audio epoch");
        yes(gate.request(plan, audio, base+4) == null, "exact old pair still terminal");
        yes(gate.request(plan-1, audio+2, base+5) == null, "older plan rejected");
        var successor = gate.request(plan+1, audio+1, base+6);
        yes(successor != null, "genuinely new plan and audio generation allowed");
        yes(!gate.acknowledgeStopped(initial, plan, audio, base+7), "stale old request rejected");
        yes(gate.acknowledgeStopped(successor, plan+1, audio+1, base+8), "successor external claim");
        yes(gate.consumeForExplicitAbort(successor, plan+1, audio+1, base+9), "successor legitimate consume");
        yes(gate.request(plan+1, audio+2, base+10) == null,
                "successor abandoned plan also terminal");
        yes(gate.request(plan+2, audio+2, base+11) != null, "second successor valid");
    }
    public static void main(String[] args) {
        seed(1,1,1_000_000_000L);
        for (int k=0; k<4096; k++) seed(1 + (k%300), 1 + k, 1_000_000_000L+k*1234L);
        System.out.println("VOICE_STOP_CONSUMED_PLAN_LINEAGE_PASS checks=" + checks);
    }
}
