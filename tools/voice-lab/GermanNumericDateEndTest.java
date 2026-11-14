import java.util.List;

/** FUTURE ONLY: dotted date as real sentence ending, without mid-date false pauses. */
public final class GermanNumericDateEndTest {
    private static int checks;
    private static void eq(List<String> expected, String source, String label) {
        List<String> chunks = GermanSpeechChunkPlanner.split(source);
        if (!chunks.equals(expected)) throw new AssertionError(label + " expected=" + expected + " actual=" + chunks);
        checks++;
        String normalized = source.replaceAll("(?U)\\s+", " ").trim();
        if (!normalized.equals(String.join(" ", chunks)))
            throw new AssertionError(label + " lost text: " + chunks);
        checks++;
    }
    public static void main(String[] args) {
        eq(List.of("Der Termin ist am 29.10.", "Danach fahre ich los."),
                "Der Termin ist am 29.10. Danach fahre ich los.", "short numeric date final");
        eq(List.of("Wir sehen uns am 01.05.", "Morgen arbeiten wir."),
                "Wir sehen uns am 01.05. Morgen arbeiten wir.", "zero-padded date final");
        eq(List.of("Der Termin ist am 29.10. um 11 Uhr.", "Danach fahren wir."),
                "Der Termin ist am 29.10. um 11 Uhr. Danach fahren wir.", "date mid-phrase");
        eq(List.of("Wir sehen uns am 29.10. 2026 mittags.", "Bis bald!"),
                "Wir sehen uns am 29.10. 2026 mittags. Bis bald!", "date followed by year");
        eq(List.of("Am 29.10.2026.", "Bitte kommen."),
                "Am 29.10.2026. Bitte kommen.", "full dotted date ends");
        eq(List.of("Wir feiern den 5. Geburtstag.", "Dann essen wir."),
                "Wir feiern den 5. Geburtstag. Dann essen wir.", "ordinal noun safe");
        String canonical = "Der Termin ist am 29.10. Danach fahre ich los.";
        char[] whitespace = {' ', '\u00a0', '\u202f', '\u2007', '\u2009'};
        for (int run = 0; run < 300; run++) {
            StringBuilder v = new StringBuilder();
            for (int i = 0, slot = 0; i < canonical.length(); i++) {
                char c = canonical.charAt(i);
                v.append(c == ' ' ? whitespace[(run + (slot++) * 7) % whitespace.length] : c);
            }
            eq(List.of("Der Termin ist am 29.10.", "Danach fahre ich los."),
                    v.toString(), "unicode numeric date " + run);
        }
        System.out.println("VOICE_GERMAN_NUMERIC_DATE_END_PASS checks=" + checks);
    }
}
