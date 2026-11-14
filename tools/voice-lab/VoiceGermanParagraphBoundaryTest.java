import java.util.List;

/** Android-free spoken German text planning: paragraph breaks are meaningful pauses. */
public final class VoiceGermanParagraphBoundaryTest {
    private static int checks;
    private static void equal(List<String> expected, String input, String caseName) {
        List<String> actual = GermanSpeechChunkPlanner.split(input);
        checks++;
        if (!actual.equals(expected))
            throw new AssertionError("#" + checks + " " + caseName
                    + " expected=" + expected + " actual=" + actual);
        String source = input.replaceAll("(?U)\\s+", " ").trim();
        checks++;
        if (!source.equals(String.join(" ", actual)))
            throw new AssertionError("#" + checks + " " + caseName + " text loss or reordering");
    }
    public static void main(String[] args) {
        equal(List.of("Erster Gedanke", "Zweiter Gedanke"),
                "Erster Gedanke\n\nZweiter Gedanke", "two LF break paragraphs");
        equal(List.of("Guten Morgen", "Und jetzt geht es weiter"),
                "Guten Morgen\r\n  \t\r\nUnd jetzt geht es weiter", "two CRLF with blank line");
        equal(List.of("Ein Gedanke", "Eine Antwort"),
                "Ein Gedanke\u2029Eine Antwort", "Unicode paragraph separator");
        equal(List.of("Eine Zeile und die nächste"),
                "Eine Zeile\nund die nächste", "single newline soft-wrap is NOT paragraph");
        equal(List.of("Ja.", "Nein!", "Weiter ohne Punkt"),
                "Ja.\n\nNein!\n\nWeiter ohne Punkt", "punctuation and paragraph mixed");
        equal(List.of("A", "B"), "\n\nA\n\n\nB\n\n", "outer and repeated breaks");
        String longToken = "A".repeat(270);
        equal(List.of(longToken, "Danach"),
                longToken + "\n\nDanach", "long token is not lost");
        // Stable deterministic stress across localized whitespace and punctuation.
        String[] separators = {"\n\n", "\r\n\r\n", "\n \t\n", "\u2029", "\n\u00a0\n"};
        for (int i = 0; i < 200; i++) {
            String sep = separators[i % separators.length];
            equal(List.of("Heute ist es schön", "Morgen sehen wir uns", "Danke!"),
                    "Heute ist es schön" + sep + "Morgen sehen wir uns" + sep + "Danke!",
                    "paragraph metamorphic " + i);
        }
        System.out.println("VOICE_GERMAN_PARAGRAPH_BOUNDARY_PASS checks=" + checks);
    }
}
