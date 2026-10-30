import java.util.List;

/** Pure Java future-only regression: numerical quantities are single spoken phrases. */
public final class GermanQuantityScalePhraseTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static void chunks(String input, String... wanted) {
        List<String> actual = GermanSpeechChunkPlanner.split(input);
        List<String> expected = List.of(wanted);
        check(actual.equals(expected), "input=" + input + " expected=" + expected + " actual=" + actual);
        check(String.join(" ", actual).equals(input.replaceAll("(?U)\\s+", " ").trim()),
                "lossless text: " + input);
    }
    public static void main(String[] args) {
        chunks("Es kostet 2 Mio. Euro. Danach sparen wir.",
                "Es kostet 2 Mio. Euro.", "Danach sparen wir.");
        chunks("Der Markt hat 3 Mrd. Menschen. Danach steigt es.",
                "Der Markt hat 3 Mrd. Menschen.", "Danach steigt es.");
        chunks("Wir haben 5 Tsd. Stück auf Lager. Dann liefern wir.",
                "Wir haben 5 Tsd. Stück auf Lager.", "Dann liefern wir.");
        chunks("Es kostet 1,5 Mio. Euro. Danach rechnen wir.",
                "Es kostet 1,5 Mio. Euro.", "Danach rechnen wir.");
        chunks("Wir hatten 10 Mio. vor Steuern. Danach handeln wir.",
                "Wir hatten 10 Mio. vor Steuern.", "Danach handeln wir.");
        chunks("Der Wert beträgt 2 Mio. Danach wird neu verhandelt.",
                "Der Wert beträgt 2 Mio.", "Danach wird neu verhandelt.");
        chunks("Die Plattform hat 3 Mio. Kunden. Danach wächst sie.",
                "Die Plattform hat 3 Mio. Kunden.", "Danach wächst sie.");
        chunks("Der Haushalt erhält 1.000 Mio. Euro. Danach erfolgt die Prüfung.",
                "Der Haushalt erhält 1.000 Mio. Euro.", "Danach erfolgt die Prüfung.");
        chunks("Mio. Danach", "Mio.", "Danach");
        chunks("Die 2. Runde ist offen. Dann spielen wir.",
                "Die 2. Runde ist offen.", "Dann spielen wir.");
        chunks("Das Treffen ist am 29.10. Danach gehen wir.",
                "Das Treffen ist am 29.10.", "Danach gehen wir.");
        chunks("Der Arzt heißt Dr. Müller. Danach kommt Frau Beck.",
                "Der Arzt heißt Dr. Müller.", "Danach kommt Frau Beck.");
        for (int n=1; n<=300; n++) {
            String amount = n + ",5";
            String input = "Wir haben " + amount + " Mio. Euro. Danach ist Schluss.";
            chunks(input, "Wir haben " + amount + " Mio. Euro.", "Danach ist Schluss.");
        }
        System.out.println("VOICE_GERMAN_QUANTITY_SCALE_PASS checks=" + checks);
    }
}
