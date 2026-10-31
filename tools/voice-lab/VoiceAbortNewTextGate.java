/**
 * FUTURE-ONLY input admission before consuming an epoch-bound STOP receipt.
 * This performs no audio operation and does not establish physical STOP proof.
 * It does not modify or normalize text; the existing planner remains owner.
 */
public final class VoiceAbortNewTextGate {
    private VoiceAbortNewTextGate() { }

    public static boolean hasNonInvisibleContent(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)
                    && !Character.isISOControl(cp)
                    && type != Character.FORMAT
                    && type != Character.SURROGATE
                    // Private-use and unassigned scalars carry no guaranteed
                    // spoken content, including Unicode noncharacters.
                    && type != Character.PRIVATE_USE
                    && type != Character.UNASSIGNED
                    // A standalone combining accent, variation selector, or vowel mark
                    // is not a speakable replacement and must not spend a STOP claim.
                    && type != Character.NON_SPACING_MARK
                    && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) return true;
        }
        return false;
    }
}
