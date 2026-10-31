import java.util.List;

/** Pure JVM, future-only German text planning regression; not acoustic quality proof. */
public final class GermanPlaceAbbreviationTest {
    private static int checks;
    private static void eq(List<String> expected, String input, String label) {
        List<String> actual = GermanSpeechChunkPlanner.split(input);
        if (!expected.equals(actual))
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        if (!input.equals(String.join(" ", actual)))
            throw new AssertionError(label + ": original text changed");
        checks += 2;
    }

    private static void eqNormalized(List<String> expected, String input, String label) {
        List<String> actual = GermanSpeechChunkPlanner.split(input);
        if (!expected.equals(actual))
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        if (!input.replaceAll("(?U)\\s+", " ").trim().equals(String.join(" ", actual)))
            throw new AssertionError(label + ": normalized text changed");
        checks += 2;
    }
    public static void main(String[] args) {
        eq(List.of("Wir fahren nach St. Pauli.", "Danach essen wir."),
                "Wir fahren nach St. Pauli. Danach essen wir.", "Sankt Pauli location");
        eq(List.of("Das Konzert ist in St. Gallen.", "Bitte rechtzeitig kommen."),
                "Das Konzert ist in St. Gallen. Bitte rechtzeitig kommen.", "Sankt Gallen location");
        eq(List.of("In St. Petersburg regnet es.", "Wir bleiben hier."),
                "In St. Petersburg regnet es. Wir bleiben hier.", "Sankt Petersburg location");
        eq(List.of("Das ist die erste Frage.", "Jetzt die zweite."),
                "Das ist die erste Frage. Jetzt die zweite.", "ordinary sentence boundaries");
        // German ordinal+noun compounds must not cause an audible mid-phrase break.
        eq(List.of("Mein Sohn geht in die 1. Klasse.", "Danach kommt die 2. Klasse."),
                "Mein Sohn geht in die 1. Klasse. Danach kommt die 2. Klasse.",
                "ordinal school class");
        eq(List.of("Wir wohnen im 3. Stock.", "Es ist ruhig."),
                "Wir wohnen im 3. Stock. Es ist ruhig.", "ordinal floor");
        eq(List.of("Sie spielt in der 2. Liga.", "Morgen gewinnt sie."),
                "Sie spielt in der 2. Liga. Morgen gewinnt sie.", "ordinal league");
        eq(List.of("Das war Punkt 1.", "Danach folgt Punkt 2."),
                "Das war Punkt 1. Danach folgt Punkt 2.",
                "true sentence end must remain boundary");
        eq(List.of("Wir treffen uns am 5. November.", "Danach fahren wir."),
                "Wir treffen uns am 5. November. Danach fahren wir.",
                "day month regression");
        eq(List.of("Wir treffen uns am 29.10.2026.", "Danach fahren wir."),
                "Wir treffen uns am 29.10.2026. Danach fahren wir.",
                "numeric date regression");
        // Regression: >=100 ordinal unit compounds still form ONE spoken phrase.
        // Do not broaden date interpretation or silence true sentence endings.
        eq(List.of("Er erreicht den 100. Platz.", "Sehr gut!"),
                "Er erreicht den 100. Platz. Sehr gut!", "three-digit rank");
        eq(List.of("Im 125. Stock wartet sie.", "Gehen wir?"),
                "Im 125. Stock wartet sie. Gehen wir?", "three-digit floor");
        eq(List.of("Die 1000. Runde beginnt.", "Jetzt!"),
                "Die 1000. Runde beginnt. Jetzt!", "four-digit round");
        eq(List.of("Es sind 100.", "Danach folgt Text."),
                "Es sind 100. Danach folgt Text.", "true numeric sentence end");
        // Date abbreviations must not split an unfinished German date phrase.
        eq(List.of("Wir treffen uns am 5. Okt. um acht.", "Danach fahren wir."),
                "Wir treffen uns am 5. Okt. um acht. Danach fahren wir.",
                "abbreviated October mid-sentence");
        eq(List.of("Am 12. Sept. 2026 ist ein Termin.", "Notiert!"),
                "Am 12. Sept. 2026 ist ein Termin. Notiert!",
                "abbreviated September with year");
        eq(List.of("Das Treffen am 1. Dez. beginnt früh.", "Gute Reise."),
                "Das Treffen am 1. Dez. beginnt früh. Gute Reise.",
                "abbreviated December before lowercase verb");
        eq(List.of("Am 5. Okt.", "Danach geht es weiter."),
                "Am 5. Okt. Danach geht es weiter.",
                "date abbreviation is also a genuine sentence end");
        eq(List.of("Es ist Okt.", "Danach kommt Text."),
                "Es ist Okt. Danach kommt Text.",
                "unanchored month abbreviation must not swallow sentence end");
        eq(List.of("Am 32.", "Okt.", "beginnt nichts."),
                "Am 32. Okt. beginnt nichts.",
                "invalid day must not be mistaken for a valid date");
        String date = "Wir kommen am 5. Okt. um acht. Danach geht es weiter.";
        char[] dateSpace = { ' ', '\u00a0', '\u202f', '\u2007', '\u2009' };
        for (int run = 0; run < 100; run++) {
            StringBuilder variant = new StringBuilder();
            int slot = 0;
            for (int i = 0; i < date.length(); i++) {
                char ch = date.charAt(i);
                variant.append(ch == ' ' ? dateSpace[(run + slot++ * 3) % dateSpace.length] : ch);
            }
            eqNormalized(List.of("Wir kommen am 5. Okt. um acht.", "Danach geht es weiter."),
                    variant.toString(), "month-abbreviation Unicode whitespace " + run);
        }
        String ordinal = "Er ist auf dem 100. Platz. Sehr gut!";
        char[] unicodeSpace = { ' ', '\u00a0', '\u202f', '\u2007', '\u2009' };
        for (int run = 0; run < 256; run++) {
            StringBuilder variant = new StringBuilder();
            int slot = 0;
            for (int i = 0; i < ordinal.length(); i++) {
                char ch = ordinal.charAt(i);
                variant.append(ch == ' ' ? unicodeSpace[(run + slot++ * 3) % unicodeSpace.length] : ch);
            }
            eqNormalized(List.of("Er ist auf dem 100. Platz.", "Sehr gut!"),
                    variant.toString(), "large-ordinal Unicode whitespace " + run);
        }
        System.out.println("VOICE_GERMAN_PLACE_ABBREVIATION_PASS checks=" + checks);
    }
}
