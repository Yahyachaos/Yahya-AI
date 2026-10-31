import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Future-only seeded JVM delivery oracle, NOT a speaker or TTS measurement. */
public final class GermanStreamingDeliveryLedgerTest {
    private static int checks;
    private static void ok(boolean value, String label) {
        if (!value) throw new AssertionError("FAIL " + label);
        checks++;
    }
    private static void eq(Object expected, Object actual, String label) {
        ok(Objects.equals(expected, actual), label + " expected=" + expected + " got=" + actual);
    }
    private static String norm(String text) {
        return text.replaceAll("(?U)\\s+", " ").trim();
    }
    public static void main(String[] args) {
        GermanStreamingDeliveryLedger d = new GermanStreamingDeliveryLedger();
        eq(GermanStreamingDeliveryLedger.Phase.COMPLETE, d.phase() == null ? null :
                d.begin("  ") > 0 ? d.phase() : null, "empty plan complete");
        String sentence = "Dr. Müller sagte: „Hallo!“ Am 29.10.2026. Danach z. B. Tee?";
        long g = d.begin(sentence);
        eq(norm(sentence), d.normalizedPlanText(), "German text intact");
        GermanStreamingDeliveryLedger.Ticket first = d.leaseNext(g);
        ok(first != null, "first ticket");
        eq(null, d.leaseNext(g), "no duplicate in-flight dispatch");
        ok(!d.playbackDrained(first), "synthesis not yet complete");
        ok(d.synthesisEnded(first), "synthesis complete");
        eq(null, d.leaseNext(g), "synthesis callback cannot advance audio cursor");
        ok(d.stop(g), "interrupt in-flight");
        eq(GermanStreamingDeliveryLedger.Phase.UNCERTAIN, d.phase(), "unknown audible coverage");
        ok(!d.playbackDrained(first), "late drain rejected");
        eq(-1L, d.restart(g, GermanStreamingDeliveryLedger.Recovery.CONTINUE_WITH_NO_IN_FLIGHT),
                "ambiguous partial audio never automatically replayed/skipped");
        long newId = d.restart(g, GermanStreamingDeliveryLedger.Recovery.REPLAY_ONLY_IF_EXTERNALLY_PROVEN_SILENT);
        ok(newId > g, "new generation on replay");
        eq(first.text, d.leaseNext(newId).text, "proven silence allows same chunk");
        ok(!d.synthesisEnded(first), "old ticket cannot complete a new generation");
        g = d.begin("Erstens. Zweitens.");
        GermanStreamingDeliveryLedger.Ticket one = d.leaseNext(g);
        ok(d.synthesisEnded(one), "first chunk synthesized");
        ok(d.stop(g), "stop before drain callback");
        long g2 = d.restart(g, GermanStreamingDeliveryLedger.Recovery.ADVANCE_ONLY_IF_EXTERNALLY_PROVEN_FULL_DRAIN);
        eq(1, d.confirmedChunks(), "externally certified full drain advances once");
        ok(!d.playbackDrained(one), "old callback cannot double advance");
        eq("Zweitens.", d.leaseNext(g2).text, "no replay of confirmed chunk");
        g = d.begin("Hallo! Weiter!");
        ok(d.stop(g), "pause without in-flight audio");
        eq(GermanStreamingDeliveryLedger.Phase.SUSPENDED, d.phase(), "clean pause");
        g2 = d.restart(g, GermanStreamingDeliveryLedger.Recovery.CONTINUE_WITH_NO_IN_FLIGHT);
        ok(g2 > g, "safe resume also rotates generation");

        Random r = new Random(0xCE11A129L);
        String[] corpus = { sentence,
                "Am 5. November um 12.50 Uhr. Dr. Mann sagt: „Ja!“ Weiter?",
                "Mail an max@example.de. URL https://beispiel.de/?p=1.2 bleibt. Fertig!",
                "X".repeat(310) + " Danach fertig." };
        for (int run = 0; run < 200; run++) {
            String text = corpus[run % corpus.length];
            long id = d.begin(text);
            List<String> heard = new ArrayList<>();
            int guard = 0;
            while (d.phase() != GermanStreamingDeliveryLedger.Phase.COMPLETE) {
                ok(guard++ < 500, "finite seeded sequence");
                if (r.nextInt(7) == 0) {
                    ok(d.stop(id), "safe pause");
                    id = d.restart(id, GermanStreamingDeliveryLedger.Recovery.CONTINUE_WITH_NO_IN_FLIGHT);
                    ok(id > 0, "resume safe epoch");
                }
                GermanStreamingDeliveryLedger.Ticket t = d.leaseNext(id);
                ok(t != null, "issued one ticket");
                eq(null, d.leaseNext(id), "cannot dispatch twice");
                ok(!d.playbackDrained(t), "drain not accepted before synth done");
                ok(d.synthesisEnded(t), "synthesis event");
                ok(!d.synthesisEnded(t), "synthesis callback not duplicated");
                if (r.nextInt(4) == 0) {
                    long old = id;
                    ok(d.stop(old), "interrupted audio");
                    boolean oracleProvesFullDrain = r.nextBoolean();
                    if (oracleProvesFullDrain) heard.add(t.text);
                    id = d.restart(old, oracleProvesFullDrain
                            ? GermanStreamingDeliveryLedger.Recovery.ADVANCE_ONLY_IF_EXTERNALLY_PROVEN_FULL_DRAIN
                            : GermanStreamingDeliveryLedger.Recovery.REPLAY_ONLY_IF_EXTERNALLY_PROVEN_SILENT);
                    ok(id > old, "new epoch");
                    ok(!d.playbackDrained(t), "stale callback has no effect");
                } else {
                    ok(d.playbackDrained(t), "actual drain acknowledged");
                    ok(!d.playbackDrained(t), "drain callback idempotent");
                    heard.add(t.text);
                }
            }
            eq(norm(text), String.join(" ", heard), "no missing or repeated text " + run);
            eq(norm(text), d.confirmedText(), "independently verified confirmed prefix " + run);
        }
        // Regression: the streaming ledger must use the same Unicode-space
        // normalization as the German planner, including across a safe STOP/restart.
        String[] unicodeSpaces = {"\u00a0", "\u202f", "\u2007", "\u2009", "\t", "\n", " "};
        for (String space : unicodeSpaces) {
            String input = "Guten Morgen!" + space + "Mein Sohn ist in der 1." + space
                    + "Klasse." + space + "Weiter!";
            long id = d.begin(input);
            List<String> expected = GermanSpeechChunkPlanner.split(input);
            eq(norm(input), d.normalizedPlanText(), "Unicode plan text intact");
            eq(expected.size(), d.chunkCount(), "Unicode chunk count");
            for (int i = 0; i < expected.size(); i++) {
                GermanStreamingDeliveryLedger.Ticket ticket = d.leaseNext(id);
                ok(ticket != null, "Unicode ticket available");
                eq(expected.get(i), ticket.text, "Unicode text chunk exactly once");
                ok(d.synthesisEnded(ticket), "Unicode synthesis completed");
                ok(d.playbackDrained(ticket), "Unicode raw drain completed");
                if (i == 0) {
                    ok(d.stop(id), "Unicode safe STOP between chunks");
                    long nextId = d.restart(id,
                            GermanStreamingDeliveryLedger.Recovery.CONTINUE_WITH_NO_IN_FLIGHT);
                    ok(nextId > id, "Unicode safe STOP rotates epoch");
                    ok(!d.playbackDrained(ticket), "Unicode prior epoch callback rejected");
                    id = nextId;
                }
            }
            eq(GermanStreamingDeliveryLedger.Phase.COMPLETE, d.phase(),
                    "Unicode complete after STOP/restart");
            eq(norm(input), d.confirmedText(), "Unicode text neither lost nor replayed");
        }
        System.out.println("VOICE_GERMAN_STREAM_LEDGER_PASS checks=" + checks);
    }
}
