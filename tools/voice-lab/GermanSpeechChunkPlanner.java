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
        String normalized = text.replaceAll("(?U)\\s+", " ").trim();
        if (normalized.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int pos = 0; pos < normalized.length(); pos++) {
            char ch = normalized.charAt(pos);
            if (ch != '.' && ch != '!' && ch != '?' && ch != '\u2026') continue;
            int punctuationEnd = pos + 1;
            // Treat clusters ("?!", "...") as a single spoken boundary candidate.
            while (punctuationEnd < normalized.length()
                    && isSentencePunctuation(normalized.charAt(punctuationEnd))) punctuationEnd++;
            int boundaryEnd = punctuationEnd;
            // German quoted sentences often close after the punctuation:
            // „Hallo!“ Danach ... / (wirklich?) Danach ...
            while (boundaryEnd < normalized.length()
                    && isClosingDelimiter(normalized.charAt(boundaryEnd))) boundaryEnd++;
            boolean boundary = boundaryEnd == normalized.length()
                    || normalized.charAt(boundaryEnd) == ' ';
            boolean abbreviation = punctuationEnd == pos + 1 && ch == '.'
                    && isAbbreviation(normalized, start, pos, boundaryEnd);
            if (boundary && !abbreviation) {
                appendLengthBounded(result, normalized.substring(start, boundaryEnd));
                start = boundaryEnd;
                pos = boundaryEnd - 1;
            } else {
                pos = punctuationEnd - 1;
            }
        }
        if (start < normalized.length()) appendLengthBounded(result, normalized.substring(start));
        return Collections.unmodifiableList(result);
    }

    private static boolean isSentencePunctuation(char c) {
        return c == '.' || c == '!' || c == '?' || c == '\u2026';
    }

    private static boolean isClosingDelimiter(char c) {
        return c == '\"' || c == '\u201d' || c == '\u201c' || c == '\u00bb'
                || c == '\u00ab' || c == '\u2039' || c == '\u203a'
                || c == '\u2019' || c == '\'' || c == ')' || c == ']';
    }

    private static boolean isAbbreviation(String text, int start, int end, int boundaryEnd) {
        int p = end - 1;
        while (p >= start && Character.isLetter(text.charAt(p))) p--;
        String token = text.substring(p + 1, end).toLowerCase(Locale.ROOT);
        if (isShortMonth(token) && isDateMonthContinuation(text, start, p + 1, boundaryEnd))
            return true;
        switch (token) {
            case "dr": case "prof": case "fr": case "hr": case "nr": case "ca":
            case "bzw": case "usw": case "vgl": case "z": case "b": case "u":
            case "a": case "d": case "h": case "evtl": case "ggf":
            // Standard professional/academic German title fragments: avoid a spoken
            // sentence pause between abbreviated qualifications and surnames.
            case "dipl": case "ing": case "med": case "kfm": case "st":
                return true;
            default:
                if (!token.isEmpty()) return false;
                // Do not split spoken numeric ordinals in dates like "am 5. November".
                int n = end - 1;
                while (n >= start && Character.isDigit(text.charAt(n))) n--;
                int digits = end - n - 1;
                if (digits == 0) return false;
                // A known ordinal+noun compound remains continuous even above 99:
                // "100. Platz" and "1000. Runde" are not sentence endings.
                // Keep the <=2-digit restriction ONLY for day-before-month dates.
                if (nextWordIsOrdinalUnit(text, boundaryEnd)) return true;
                // Protect a numeric date only for a real calendar day, not "32. Okt.".
                // The month abbreviation itself is separately checked for continuity.
                if (digits <= 2 && nextWordIsMonth(text, boundaryEnd)) {
                    int day = Integer.parseInt(text.substring(n + 1, end));
                    if (day >= 1 && day <= 31) return true;
                }
                // "29.10." is a date fragment, but "29.10.2026." completes a
                // four-digit year and may end a sentence. Keep this conservative.
                return digits <= 2 && n >= start + 1 && text.charAt(n) == '.'
                        && Character.isDigit(text.charAt(n - 1));
        }
    }

    /** A short month is an abbreviation only in a 1..31 day/date context. */
    private static boolean isShortMonth(String token) {
        switch (token) {
            case "jan": case "feb": case "mär": case "mrz": case "apr":
            case "jun": case "jul": case "aug": case "sep": case "sept":
            case "okt": case "nov": case "dez": return true;
            default: return false;
        }
    }

    /** Do not swallow an actual sentence ending immediately after "5. Okt.". */
    private static boolean isDateMonthContinuation(String text, int start,
            int monthStart, int boundaryEnd) {
        if (boundaryEnd >= text.length() || text.charAt(boundaryEnd) != ' '
                || boundaryEnd + 1 >= text.length()) return false;
        int next = boundaryEnd + 1;
        if (!Character.isLowerCase(text.charAt(next))) {
            // A four-digit year may follow "5. Okt. 2026"; an upper-case
            // "Danach" instead means the abbreviated date ended a sentence.
            int yearEnd = next;
            while (yearEnd < text.length() && Character.isDigit(text.charAt(yearEnd)))
                yearEnd++;
            if (yearEnd - next != 4
                    || (yearEnd < text.length() && Character.isLetterOrDigit(text.charAt(yearEnd))))
                return false;
        }
        int dot = monthStart - 1;
        while (dot >= start && text.charAt(dot) == ' ') dot--;
        if (dot < start || text.charAt(dot) != '.') return false;
        int dayEnd = dot;
        int dayStart = dayEnd;
        while (dayStart > start && Character.isDigit(text.charAt(dayStart - 1))) dayStart--;
        if (dayEnd - dayStart < 1 || dayEnd - dayStart > 2
                || (dayStart > start && Character.isLetterOrDigit(text.charAt(dayStart - 1))))
            return false;
        int day = Integer.parseInt(text.substring(dayStart, dayEnd));
        return day >= 1 && day <= 31;
    }

    private static boolean nextWordIsMonth(String text, int boundaryEnd) {
        if (boundaryEnd >= text.length() || text.charAt(boundaryEnd) != ' ') return false;
        int start = boundaryEnd + 1;
        int end = start;
        while (end < text.length() && Character.isLetter(text.charAt(end))) end++;
        String month = text.substring(start, end).toLowerCase(Locale.ROOT);
        switch (month) {
            case "januar": case "februar": case "märz": case "april": case "mai":
            case "juni": case "juli": case "august": case "september":
            case "oktober": case "november": case "dezember":
                return true;
            default: return isShortMonth(month) && end < text.length()
                    && text.charAt(end) == '.';
        }
    }

    /** Known ordinal+noun compounds: do not create a spoken pause inside them. */
    private static boolean nextWordIsOrdinalUnit(String text, int boundaryEnd) {
        if (boundaryEnd >= text.length() || text.charAt(boundaryEnd) != ' ') return false;
        int wordStart = boundaryEnd + 1;
        int wordEnd = wordStart;
        while (wordEnd < text.length() && Character.isLetter(text.charAt(wordEnd))) wordEnd++;
        String word = text.substring(wordStart, wordEnd).toLowerCase(Locale.ROOT);
        switch (word) {
            case "klasse": case "stock": case "etage": case "liga":
            case "runde": case "platz":
            // A bounded list of frequent numeric-ordinal phrases. Without
            // these, "5. Geburtstag" and "3. Kapitel" become separate TTS
            // chunks and introduce a false mid-phrase pause. Keep this list
            // conservative so standalone numeric sentence endings still split.
            case "geburtstag": case "versuch": case "kapitel":
            case "minute": case "jahrhundert":
                return true;
            default: return false;
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
