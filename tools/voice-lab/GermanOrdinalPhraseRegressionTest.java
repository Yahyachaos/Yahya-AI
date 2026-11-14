import java.util.List;

/** FUTURE ONLY: deterministic German ordinal-phrase chunk boundaries; no audio or device proof. */
public final class GermanOrdinalPhraseRegressionTest {
    private static int checks;

    private static void equal(List<String> expected, String input, String label) {
        List<String> got = GermanSpeechChunkPlanner.split(input);
        if (!expected.equals(got)) throw new AssertionError(
                label + " expected=" + expected + " got=" + got);
        checks++;
        String normalized = input.replaceAll("(?U)\\s+", " ").trim();
        if (!normalized.equals(String.join(" ", got))) throw new AssertionError(
                label + " lost or changed words");
        checks++;
    }

    public static void main(String[] args) {
        equal(List.of("Wir feiern den 5. Geburtstag.", "Danach essen wir Kuchen."),
                "Wir feiern den 5. Geburtstag. Danach essen wir Kuchen.", "birthday ordinal");
        equal(List.of("Das ist der 2. Versuch.", "Weiter geht es."),
                "Das ist der 2. Versuch. Weiter geht es.", "attempt ordinal");
        equal(List.of("Wir lesen das 3. Kapitel.", "Dann machen wir Pause."),
                "Wir lesen das 3. Kapitel. Dann machen wir Pause.", "chapter ordinal");
        equal(List.of("Er wartet seit der 10. Minute.", "Noch immer."),
                "Er wartet seit der 10. Minute. Noch immer.", "minute ordinal");
        equal(List.of("Das geschah im 21. Jahrhundert.", "Geschichte endet."),
                "Das geschah im 21. Jahrhundert. Geschichte endet.", "century ordinal");
        // Do not swallow real end-of-sentence numeric boundaries or alter text.
        equal(List.of("Es sind 5.", "Danach geht es los."),
                "Es sind 5. Danach geht es los.", "numeric sentence remains boundary");
        equal(List.of("Wir treffen uns am 5. November.", "Bis dann!"),
                "Wir treffen uns am 5. November. Bis dann!", "date ordinal unchanged");
        equal(List.of("Mein Sohn geht in die 1. Klasse.", "Danke!"),
                "Mein Sohn geht in die 1. Klasse. Danke!", "existing ordinal unchanged");
        String baseline = "Wir feiern den 5. Geburtstag. Im 21. Jahrhundert geht es weiter!";
        List<String> expected = List.of("Wir feiern den 5. Geburtstag.",
                "Im 21. Jahrhundert geht es weiter!");
        char[] spaces = {' ', '\u00a0', '\u202f', '\u2007', '\u2009'};
        for (int round = 0; round < 300; round++) {
            StringBuilder variant = new StringBuilder();
            int slot = 0;
            for (int i = 0; i < baseline.length(); i++) {
                char c = baseline.charAt(i);
                variant.append(c == ' ' ? spaces[(round + slot++ * 7) % spaces.length] : c);
            }
            equal(expected, variant.toString(), "Unicode ordinal variant " + round);
        }
        System.out.println("VOICE_GERMAN_ORDINAL_PHRASE_PASS checks=" + checks);
    }
}
