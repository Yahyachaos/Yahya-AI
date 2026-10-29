import java.util.List;

/** Future-only deterministic German text edge-case regression; no synthesized audio. */
public final class GermanSpeechChunkPlannerRegressionTest {
    private static int checks;
    private static void eq(List<String> expected, String text, String label) {
        List<String> got = GermanSpeechChunkPlanner.split(text);
        if (!expected.equals(got)) throw new AssertionError("FAIL " + label + " expected=" + expected + " got=" + got);
        checks++;
        String normalized = text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        if (!normalized.equals(String.join(" ", got))) throw new AssertionError("TEXT LOSS " + label);
        checks++;
    }
    public static void main(String[] args) {
        eq(List.of("Sie schrieb: \"Hallo!\"", "Danach ging sie."),
                "Sie schrieb: \"Hallo!\" Danach ging sie.", "quoted exclamation sentence ending");
        eq(List.of("Er fragte: „Wirklich?“", "Dann nickte sie."),
                "Er fragte: „Wirklich?“ Dann nickte sie.", "German closing quotation mark");
        eq(List.of("Antwort (ja!)", "Weiter geht's."),
                "Antwort (ja!) Weiter geht's.", "punctuation inside closing parenthesis");
        eq(List.of("Wirklich?!", "Ja."), "Wirklich?! Ja.", "compound emphatic ending");
        eq(List.of("Mail an max@example.de senden.", "Danke!"),
                "Mail an max@example.de senden. Danke!", "email address in sentence");
        eq(List.of("Mehr unter https://beispiel.de/hilfe?x=1.2 nachlesen.", "Fertig."),
                "Mehr unter https://beispiel.de/hilfe?x=1.2 nachlesen. Fertig.", "URL query kept intact");
        eq(List.of("Wir treffen uns am 5. November um 9 Uhr.", "Bis dann!"),
                "Wir treffen uns am 5. November um 9 Uhr. Bis dann!", "day ordinal before German month");
        eq(List.of("Am 29.10.2026.", "Bitte melden."),
                "Am 29.10.2026. Bitte melden.", "full numeric date followed by sentence ending");
        String token = "X".repeat(310);
        eq(List.of(token, "Danach weiter."), token + " Danach weiter.", "unbroken token not silently truncated");
        System.out.println("VOICE_GERMAN_ADVERSARIAL_PASS checks=" + checks);
    }
}
