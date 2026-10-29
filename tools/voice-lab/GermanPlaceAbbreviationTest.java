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
        System.out.println("VOICE_GERMAN_PLACE_ABBREVIATION_PASS checks=" + checks);
    }
}
