import java.util.Random;
/** Pure JVM-only candidate admission tests, NOT integrated speaker/runtime proof. */
public final class VoiceAbortNewTextGateTest {
    private static long checks;
    private static void ok(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message + " check=" + checks);
    }
    public static void main(String[] args) {
        ok(!VoiceAbortNewTextGate.hasNonInvisibleContent(null), "null");
        ok(!VoiceAbortNewTextGate.hasNonInvisibleContent(""), "empty");
        ok(!VoiceAbortNewTextGate.hasNonInvisibleContent(" \t\r\n\u2003\u00a0\u202f"), "unicode spaces");
        ok(!VoiceAbortNewTextGate.hasNonInvisibleContent("\u200b\u2060\u200d\ufeff"), "format only");
        ok(!VoiceAbortNewTextGate.hasNonInvisibleContent("\u0000\u0007\u001f"), "controls only");
        ok(!VoiceAbortNewTextGate.hasNonInvisibleContent("\ud800"), "isolated high surrogate");
        ok(!VoiceAbortNewTextGate.hasNonInvisibleContent("\udc00"), "isolated low surrogate");
        ok(VoiceAbortNewTextGate.hasNonInvisibleContent("Neu."), "plain German text");
        ok(VoiceAbortNewTextGate.hasNonInvisibleContent("\tNeu. \n"), "surrounded text");
        ok(VoiceAbortNewTextGate.hasNonInvisibleContent("Hallo!"), "punctuation text");
        ok(VoiceAbortNewTextGate.hasNonInvisibleContent("..."), "do not impose a lexical policy");
        ok(VoiceAbortNewTextGate.hasNonInvisibleContent("\uD83D\uDE42"), "emoji remains a candidate");
        ok(VoiceAbortNewTextGate.hasNonInvisibleContent("مرحبا"), "Arabic nonempty");
        ok(VoiceAbortNewTextGate.hasNonInvisibleContent("中文"), "CJK nonempty");
        int[] hidden = {' ','\t','\n','\r',0x00a0,0x2003,0x202f,0x200b,0x200d,0xfeff,0x2060,0};
        Random random = new Random(0x4C53544F505L);
        for (int i = 0; i < 4000; i++) {
            StringBuilder b = new StringBuilder();
            int n = 1 + random.nextInt(65);
            for (int j = 0; j < n; j++) b.appendCodePoint(hidden[random.nextInt(hidden.length)]);
            String invisible = b.toString();
            ok(!VoiceAbortNewTextGate.hasNonInvisibleContent(invisible), "no invisible-only replacement " + i);
            int at = random.nextInt(b.length() + 1);
            b.insert(at, i % 2 == 0 ? 'Ä' : '7');
            ok(VoiceAbortNewTextGate.hasNonInvisibleContent(b.toString()), "one visible codepoint " + i);
            ok(!VoiceAbortNewTextGate.hasNonInvisibleContent(invisible), "no mutation of original " + i);
        }
        System.out.println("VOICE_ABORT_NEW_TEXT_ADMISSION_LOCAL_PASS checks=" + checks);
    }
}
