import java.util.List;

/** FUTURE ONLY: a terminating "usw." may end a spoken sentence; not speaker evidence. */
public final class GermanTerminalAbbreviationTest {
    private static int checks;
    private static void eq(List<String> expected, String text, String label) {
        List<String> got = GermanSpeechChunkPlanner.split(text);
        if (!got.equals(expected)) throw new AssertionError(label + " expected=" + expected + " actual=" + got);
        checks++;
        String normalized = text.replaceAll("(?U)\\s+", " ").trim();
        if (!normalized.equals(String.join(" ", got))) throw new AssertionError(label + " text changed");
        checks++;
    }
    public static void main(String[] args) {
        eq(List.of("Sie kauft Brot, Milch usw.", "Danach fährt sie heim."),
                "Sie kauft Brot, Milch usw. Danach fährt sie heim.", "terminal usw before new sentence");
        eq(List.of("Sie kauft Brot usw. für das Fest.", "Danach fährt sie heim."),
                "Sie kauft Brot usw. für das Fest. Danach fährt sie heim.", "usw in running sentence");
        eq(List.of("Sie kauft Brot usw."), "Sie kauft Brot usw.", "usw at end of text");
        eq(List.of("Dr. Müller nennt Brot usw.", "Danach geht er."),
                "Dr. Müller nennt Brot usw. Danach geht er.", "preserve doctor title");
        eq(List.of("Ich möchte z. B. Brot essen.", "Danke."),
                "Ich möchte z. B. Brot essen. Danke.", "preserve multi-part abbreviation");
        eq(List.of("Wir kaufen Brot usw.\"", "Danach fahren wir."),
                "Wir kaufen Brot usw.\" Danach fahren wir.", "quoted terminal abbreviation");
        eq(List.of("Der Termin ist am 29.10.", "Danach fahren wir."),
                "Der Termin ist am 29.10. Danach fahren wir.", "date end unaffected");
        eq(List.of("Wir feiern den 5. Geburtstag.", "Danach tanzen wir."),
                "Wir feiern den 5. Geburtstag. Danach tanzen wir.", "ordinal noun unaffected");
        eq(List.of("Wir treffen uns am 5. Okt. um acht.", "Danach fahren wir."),
                "Wir treffen uns am 5. Okt. um acht. Danach fahren wir.", "month abbreviation unaffected");
        String canonical = "Sie kauft Brot, Milch usw. Danach fährt sie heim.";
        char[] whitespace = { ' ', '\u00a0', '\u202f', '\u2007', '\u2009' };
        for (int i = 0; i < 256; i++) {
            StringBuilder variant = new StringBuilder();
            int slot = 0;
            for (int j = 0; j < canonical.length(); j++) {
                char c = canonical.charAt(j);
                variant.append(c == ' ' ? whitespace[(i + slot++ * 7) % whitespace.length] : c);
            }
            eq(List.of("Sie kauft Brot, Milch usw.", "Danach fährt sie heim."), variant.toString(),
                    "unicode space " + i);
        }
        System.out.println("VOICE_GERMAN_TERMINAL_ABBREVIATION_PASS checks=" + checks);
    }
}
