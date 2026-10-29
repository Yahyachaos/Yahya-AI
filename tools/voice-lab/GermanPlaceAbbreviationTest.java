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
        System.out.println("VOICE_GERMAN_PLACE_ABBREVIATION_PASS checks=" + checks);
    }
}
