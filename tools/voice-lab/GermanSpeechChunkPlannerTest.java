import java.util.List;

/** Pure Java text-planning tests; no acoustic or voice-quality claims. */
public final class GermanSpeechChunkPlannerTest {
    private static int checks;
    private static void eq(Object exp, Object got, String msg) {
        if (!exp.equals(got)) throw new AssertionError("FAIL " + msg + "; expected=" + exp + " got=" + got);
        checks++;
    }
    private static void ok(boolean valid, String msg) {
        if (!valid) throw new AssertionError("FAIL " + msg);
        checks++;
    }

    public static void main(String[] args) {
        eq(List.of(), GermanSpeechChunkPlanner.split(null), "null silent");
        eq(List.of(), GermanSpeechChunkPlanner.split("  \t"), "whitespace silent");
        eq(List.of("Hallo!", "Wie geht's?", "Mir geht's gut."),
                GermanSpeechChunkPlanner.split("  Hallo!  Wie geht's?  Mir geht's gut. "), "ordinary German sentences");
        eq(List.of("Dr. Müller kommt.", "Er sagt Hallo."),
                GermanSpeechChunkPlanner.split("Dr. Müller kommt. Er sagt Hallo."), "doctor title stays in sentence");
        eq(List.of("Ich möchte z. B. Tee trinken.", "Danke!"),
                GermanSpeechChunkPlanner.split("Ich möchte z. B. Tee trinken. Danke!"), "z. B. is not speech boundary");
        eq(List.of("Ich wohne in der Nr. 4.", "Danke."),
                GermanSpeechChunkPlanner.split("Ich wohne in der Nr. 4. Danke."), "numeric ordinal kept with context");
        eq(List.of("Das kostet 12.50 Euro.", "Bezahlt."),
                GermanSpeechChunkPlanner.split("Das kostet 12.50 Euro. Bezahlt."), "decimal is not a boundary");
        eq(List.of("Der Termin ist am 29.10. um 11 Uhr.", "Bitte kommen."),
                GermanSpeechChunkPlanner.split("Der Termin ist am 29.10. um 11 Uhr. Bitte kommen."),
                "German numeric date is not split");
        eq(List.of("Na klar...", "Danke!"),
                GermanSpeechChunkPlanner.split("Na klar... Danke!"), "ellipsis remains literal punctuation");
        eq(List.of("Bitte sage nicht Punkt Punkt."),
                GermanSpeechChunkPlanner.split("Bitte sage nicht Punkt Punkt."), "no synthesized or added Punkt words");
        eq(List.of("Das bleibt **fett** und enthält \"Anführungen\"."),
                GermanSpeechChunkPlanner.split("Das bleibt **fett** und enthält \"Anführungen\"."),
                "planning preserves markup instead of silently rewriting text");
        eq(List.of("Moin!", "Noch wach?"),
                GermanSpeechChunkPlanner.split("Moin!\u00a0 Noch wach?"), "nonbreaking spaces normalized");

        StringBuilder longSentence = new StringBuilder();
        for (int i = 0; i < 100; i++) longSentence.append("Wörter ");
        longSentence.append("fertig.");
        List<String> chunks = GermanSpeechChunkPlanner.split(longSentence.toString());
        ok(chunks.size() >= 3, "long monologue split into manageable parts");
        for (String part : chunks) {
            ok(part.length() <= 240, "normal words bound chunk length");
            ok(!part.isBlank(), "no empty chunks");
        }
        eq(longSentence.toString().trim(), String.join(" ", chunks), "no text dropped during chunking");
        System.out.println("VOICE_GERMAN_TEXT_PLANNER_PASS checks=" + checks);
    }
}
