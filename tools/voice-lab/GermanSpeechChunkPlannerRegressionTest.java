import java.util.List;

/** Future-only deterministic German text edge-case regression; no synthesized audio. */
public final class GermanSpeechChunkPlannerRegressionTest {
    private static int checks;
    private static void eq(List<String> expected, String text, String label) {
        List<String> got = GermanSpeechChunkPlanner.split(text);
        if (!expected.equals(got)) throw new AssertionError("FAIL " + label + " expected=" + expected + " got=" + got);
        checks++;
        String normalized = text.replaceAll("(?U)\\s+", " ").trim();
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
        eq(List.of("Herr Dipl.-Ing. Müller spricht.", "Vielen Dank!"),
                "Herr Dipl.-Ing. Müller spricht. Vielen Dank!", "hyphenated engineering title");
        eq(List.of("Frau Dr. med. Scholz kommt.", "Guten Morgen!"),
                "Frau Dr. med. Scholz kommt. Guten Morgen!", "medical doctoral title");
        eq(List.of("Herr Dipl. Kfm. Weber ist da.", "Schön!"),
                "Herr Dipl. Kfm. Weber ist da. Schön!", "business graduation title");
        eq(List.of("Sie sagte: »Hallo!«", "Danach ging sie."),
                "Sie sagte: »Hallo!« Danach ging sie.", "German guillemet closing-left boundary");
        eq(List.of("Er fragte: ›Kommst du?‹", "Dann wartete er."),
                "Er fragte: ›Kommst du?‹ Dann wartete er.", "single German guillemet closing-left boundary");
        eq(List.of("Sie rief: «Ja!»", "Weiter."),
                "Sie rief: «Ja!» Weiter.", "French guillemet closing-right boundary");
        eq(List.of("Na gut…", "Danach gehen wir."),
                "Na gut… Danach gehen wir.", "single Unicode ellipsis is a sentence pause");
        eq(List.of("Er fragte?!…", "Dann wurde es still."),
                "Er fragte?!… Dann wurde es still.", "mixed punctuation and Unicode ellipsis boundary");
        // Unicode spacing occurs in pasted German text, formatted dates and quotes.
        // It must not fuse two spoken sentence chunks or separate an ordinal noun.
        eq(List.of("Guten Morgen!", "Wie geht es dir?"),
                "Guten Morgen!\u202fWie geht es dir?", "narrow no-break after exclamation");
        eq(List.of("Ja.", "Bitte."),
                "Ja.\u2007Bitte.", "figure-space after period");
        eq(List.of("Sie sagte: „Hallo!“", "Danach lachte er."),
                "Sie sagte: „Hallo!“\u202fDanach lachte er.", "quoted German speech");
        eq(List.of("Heute ist der 5. November.", "Danach Feierabend!"),
                "Heute ist der 5.\u202fNovember.\u202fDanach Feierabend!",
                "ordinal month and narrow-space boundary");
        eq(List.of("Das kostet 12.50 Euro.", "Danke!"),
                "Das kostet 12.50\u2007Euro.\u202fDanke!", "decimal not split");
        eq(List.of("Erst die 1. Klasse.", "Dann die 2. Klasse."),
                "Erst die 1.\u202fKlasse.\u202fDann die 2. Klasse.",
                "ordinal class not interrupted");
        // Deterministic metamorphic stress: Unicode/ASCII spaces yield the
        // same phrase plan and no silently dropped text, over 256 variants.
        String base = "Dr. Müller ruft an. Mein Sohn ist in der 1. Klasse. Danke!";
        List<String> baseline = GermanSpeechChunkPlanner.split(base);
        char[] spacing = { ' ', '\u00a0', '\u202f', '\u2007', '\u2009' };
        for (int run = 0; run < 256; run++) {
            StringBuilder copy = new StringBuilder();
            int slot = 0;
            for (int pos = 0; pos < base.length(); pos++) {
                char ch = base.charAt(pos);
                copy.append(ch == ' ' ? spacing[(run + slot++ * 7) % spacing.length] : ch);
            }
            eq(baseline, copy.toString(), "metamorphic Unicode spacing " + run);
        }
        System.out.println("VOICE_GERMAN_ADVERSARIAL_PASS checks=" + checks);
    }
}
