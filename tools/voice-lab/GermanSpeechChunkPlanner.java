import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * FUTURE-ONLY, Android-free conservative sentence chunk plan for spoken German.
 * It does not synthesize sound, add verbal punctuation or rewrite the user's words.
 * A model/device listening test is mandatory before enabling this in production.
 */
public final class GermanSpeechChunkPlanner {
    private static final int MAX_CHARS = 240;

    private GermanSpeechChunkPlanner() {}

    public static List<String> split(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        String normalized = text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int pos = 0; pos < normalized.length(); pos++) {
            char ch = normalized.charAt(pos);
            boolean ending = ch == '.' || ch == '!' || ch == '?';
            boolean boundary = pos + 1 == normalized.length() || normalized.charAt(pos + 1) == ' ';
            if (ending && boundary && !isAbbreviation(normalized, start, pos, ch)) {
                appendLengthBounded(result, normalized.substring(start, pos + 1));
                start = pos + 1;
            }
        }
        if (start < normalized.length()) appendLengthBounded(result, normalized.substring(start));
        return Collections.unmodifiableList(result);
    }

    private static boolean isAbbreviation(String text, int start, int end, char ch) {
        if (ch != '.') return false;
        int p = end - 1;
        while (p >= start && Character.isLetter(text.charAt(p))) p--;
        String token = text.substring(p + 1, end).toLowerCase(Locale.ROOT);
        switch (token) {
            case "dr": case "prof": case "fr": case "hr": case "nr": case "ca":
            case "bzw": case "usw": case "vgl": case "z": case "b": case "u":
            case "a": case "d": case "h": case "evtl": case "ggf":
                return true;
            default:
                // German ordinal/date fragments: never start an unrelated sentence
                // solely because a numeric prefix (e.g., 29.10.) ends in a dot.
                if (!token.isEmpty()) return false;
                // Date fragment 29.10. has a preceding digit-dot-digit sequence;
                // sentence-ending "Nr. 4." does not, and may form a boundary.
                int n = end - 1;
                while (n >= start && Character.isDigit(text.charAt(n))) n--;
                return n >= start + 1 && text.charAt(n) == '.'
                        && Character.isDigit(text.charAt(n - 1));
        }
    }

    private static void appendLengthBounded(List<String> result, String part) {
        String remaining = part.trim();
        while (remaining.length() > MAX_CHARS) {
            int splitAt = remaining.lastIndexOf(' ', MAX_CHARS);
            if (splitAt < 1) {
                // Never split a single long word; this is a planning-only contract.
                splitAt = remaining.indexOf(' ');
                if (splitAt < 1) break;
            }
            result.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt + 1).trim();
        }
        if (!remaining.isEmpty()) result.add(remaining);
    }
}
