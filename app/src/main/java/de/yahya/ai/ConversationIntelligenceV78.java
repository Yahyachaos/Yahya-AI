package de.yahya.ai;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic conversation-context policy for Celin.
 *
 * Keeps real follow-ups attached to more prior context without letting the
 * request grow without bounds. This class is deliberately Android-free so the
 * policy can be tested independently from UI, voice and avatar lifecycle code.
 */
final class ConversationIntelligenceV78 {
    static final int NORMAL_CHAR_BUDGET = 10500;
    static final int FOLLOW_UP_CHAR_BUDGET = 16000;
    static final int NORMAL_MAX_TURNS = 20;
    static final int FOLLOW_UP_MAX_TURNS = 28;

    static final class Turn {
        final String role;
        final String content;

        Turn(String role, String content) {
            this.role = role == null ? "" : role;
            this.content = content == null ? "" : content;
        }
    }

    private ConversationIntelligenceV78() {}

    static int selectContextStart(List<Turn> turns, String userText) {
        if (turns == null || turns.isEmpty()) return 0;

        boolean followUp = looksLikeFollowUp(userText);
        int charBudget = followUp ? FOLLOW_UP_CHAR_BUDGET : NORMAL_CHAR_BUDGET;
        int maxTurns = followUp ? FOLLOW_UP_MAX_TURNS : NORMAL_MAX_TURNS;
        int minTurns = followUp ? 10 : 6;
        int chars = 0;
        int count = 0;
        int start = turns.size() - 1;

        for (int i = turns.size() - 1; i >= 0; i--) {
            Turn turn = turns.get(i);
            int cost = turn.role.length() + turn.content.length() + 16;
            if (count >= maxTurns) break;
            if (count >= minTurns && chars + cost > charBudget) break;
            start = i;
            chars += cost;
            count++;
        }

        // Never start the retained window with an orphan assistant answer when
        // its immediately preceding user turn is still available.
        if (start > 0 && "assistant".equals(turns.get(start).role)) start--;
        return Math.max(0, start);
    }

    static boolean looksLikeFollowUp(String text) {
        if (text == null) return false;
        String value = text.trim().toLowerCase(Locale.GERMANY);
        if (value.isEmpty()) return false;

        // Short acknowledgement/question forms are strong continuation signals,
        // but short length alone is not: "Erkläre Relativität" is a new intent.
        if (value.matches("^(ok|okay|ja|nein|genau|klar|richtig|und|aber|also|warum|wieso|wie|was|welche|welcher|welches|nochmal|noch einmal|dann|jetzt)([ ?!,.].*)?$")) return true;
        if (value.matches("^(und|aber|also|warum|wieso|nochmal|noch einmal|dann)[ ?!,.].*")) return true;

        // Require demonstrative/reference language rather than ordinary German
        // articles such as der/die/den, which caused false positives.
        return value.matches(".*\\b(das|dies|dieser|diese|dieses|davon|dazu|damit|deshalb|dann|dort|hier|so|vorher|eben|gerade)\\b.*")
                || value.contains("meinst du damit")
                || value.contains("wie vorher")
                || value.contains("was ist damit");
    }

    static String instructionSuffix(String userText) {
        String continuity = looksLikeFollowUp(userText)
                ? "Die aktuelle Nachricht wirkt wie eine Fortsetzung. Löse Pronomen und kurze Verweise zuerst aus dem unmittelbar vorherigen Gespräch auf. "
                : "Nutze frühere Gesprächszüge nur, wenn sie für die aktuelle Absicht wirklich relevant sind. ";
        return "\nGesprächslogik: " + continuity
                + "Beantworte Folgefragen auf Basis des bereits Gesagten, statt die vorige Antwort vollständig zu wiederholen. "
                + "Wenn sich seit der letzten Antwort nur ein Detail geändert hat, nenne vor allem dieses neue Detail. "
                + "Bei echter Mehrdeutigkeit nenne kurz die plausibelste Lesart und stelle höchstens eine gezielte Rückfrage, aber nur wenn eine falsche Annahme die Antwort wesentlich verändern würde. "
                + "Erfinde keine fehlenden Fakten. Trenne sicher Gewusstes klar von Unsicherem und sage knapp, was zur Klärung fehlt. "
                + "Vermeide identische Einstiege, Wiederholungen und unnötige Zusammenfassungen aus den letzten Antworten.";
    }

    static String recoveryMessage(Throwable error) {
        String message = error == null || error.getMessage() == null
                ? ""
                : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("429") || message.contains("rate")) {
            return "Die KI ist gerade ausgelastet. Unser Gesprächskontext bleibt erhalten; versuch die Nachricht bitte gleich noch einmal.";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "Die Antwort hat gerade zu lange gebraucht. Unser Gesprächskontext bleibt erhalten; versuch es bitte noch einmal.";
        }
        return "Ich konnte die Antwort gerade nicht zuverlässig abrufen. Unser Gesprächskontext bleibt erhalten; versuch die Nachricht bitte noch einmal.";
    }
}
