package de.yahya.ai;

/**
 * Converts assistant display text into text that is safe and natural to speak.
 *
 * This class has no Android dependencies so the same normalization can later
 * be reused by local and online TTS engines.
 */
public final class SpeechTextNormalizer {
    private SpeechTextNormalizer() {}

    public static String clean(String text) {
        if (text == null) return "";

        String x = text;
        x = x.replaceAll("(?m)^#{1,6}\\s*", "");
        x = x.replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("•", ", ")
                .replace("…", ",")
                .replace("–", ",")
                .replace("—", ",");
        x = x.replaceAll("(?i)https?://\\S+", " Link " );
        x = x.replaceAll("\\.{2,}", ".");
        x = x.replaceAll("[\\[\\]{}<>*_#|]", " " );

        StringBuilder spoken = new StringBuilder();
        for (int i = 0; i < x.length();) {
            int codePoint = x.codePointAt(i);
            i += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            boolean emojiRange =
                    (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
                    || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                    || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                    || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF);
            if (emojiRange || type == Character.OTHER_SYMBOL) continue;
            spoken.appendCodePoint(codePoint);
        }

        return spoken.toString()
                .replaceAll("\\s+", " " )
                .replaceAll("\\s+([,.!?;:])", "$1")
                .trim();
    }
}
