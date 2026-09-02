package de.yahya.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic conversation-context policy for Celin.
 *
 * Keeps real follow-ups and user corrections attached to the right prior context
 * without letting the request grow without bounds. This class is deliberately
 * Android-free so the policy can be tested independently from UI, voice and
 * avatar lifecycle code.
 */
final class ConversationIntelligenceV78 {
    static final int NORMAL_CHAR_BUDGET = 10500;
    static final int FOLLOW_UP_CHAR_BUDGET = 16000;
    static final int NORMAL_MAX_TURNS = 20;
    static final int FOLLOW_UP_MAX_TURNS = 28;
    private static final int TOPICAL_LOOKBACK_TURNS = 36;

    private static final Set<String> STOP_WORDS = new HashSet<>();

    static {
        String[] words = {
                "aber", "alle", "also", "auch", "dann", "dass", "deine", "deiner", "dem", "den",
                "der", "des", "die", "dies", "diese", "dieser", "dieses", "doch", "du", "eine",
                "einem", "einen", "einer", "eines", "er", "es", "für", "hier", "ich", "ihm", "ihn",
                "ihr", "ihre", "im", "in", "ist", "ja", "jetzt", "kann", "kein", "keine", "mal",
                "mein", "meine", "mit", "nicht", "noch", "oder", "schon", "sie", "so", "und", "vom",
                "von", "war", "was", "wie", "wir", "wird", "wo", "zu", "zum", "zur"
        };
        for (String word : words) STOP_WORDS.add(word);
    }

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

        boolean continuation = looksLikeFollowUp(userText) || looksLikeCorrection(userText);
        int charBudget = continuation ? FOLLOW_UP_CHAR_BUDGET : NORMAL_CHAR_BUDGET;
        int maxTurns = continuation ? FOLLOW_UP_MAX_TURNS : NORMAL_MAX_TURNS;
        int minTurns = continuation ? 10 : 6;
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

        // If the current message contains actual topic words, keep the most recent
        // matching user anchor when it still fits the bounded continuation window.
        // This helps "Und was ist mit der Kamera?" bind to the recent camera topic
        // instead of only to the immediately preceding assistant wording.
        if (continuation) {
            int topicalAnchor = findTopicalAnchor(turns, userText, start);
            if (topicalAnchor >= 0 && topicalAnchor < start) {
                int candidateStart = topicalAnchor;
                if (candidateStart > 0 && "assistant".equals(turns.get(candidateStart).role)) {
                    candidateStart--;
                }
                if (fitsWindow(turns, candidateStart, charBudget, maxTurns)) start = candidateStart;
            }
        }

        // Never start the retained window with an orphan assistant answer when
        // its immediately preceding user turn is still available.
        if (start > 0 && "assistant".equals(turns.get(start).role)) start--;
        return Math.max(0, start);
    }

    static boolean looksLikeFollowUp(String text) {
        if (text == null) return false;
        String value = normalize(text);
        if (value.isEmpty()) return false;

        // Bare acknowledgements/action continuations are strong continuation signals.
        if (value.matches("^(ok|okay|ja|nein|genau|klar|richtig|und|aber|also|warum|wieso|wie|was|welche|welcher|welches|nochmal|noch einmal|dann|jetzt|weiter|los|mehr)[ ?!,.]*$")) return true;
        if (value.matches("^(und|aber|also|warum|wieso|nochmal|noch einmal|dann|weiter|jetzt)[ ?!,.].*")) return true;
        if (value.matches("^(mach|mache|arbeite) (weiter|das weiter|dort weiter)[ ?!,.]*$")) return true;
        if (value.matches("^(und jetzt|was jetzt|wie weiter|weiter so|mehr davon)[ ?!,.]*$")) return true;

        // Require demonstrative/reference language rather than ordinary German articles.
        return value.matches(".*\\b(das|dies|dieser|diese|dieses|davon|dazu|damit|deshalb|dann|dort|hier|so|vorher|eben|gerade|weiter)\\b.*")
                || value.contains("meinst du damit")
                || value.contains("wie vorher")
                || value.contains("was ist damit");
    }

    static boolean looksLikeCorrection(String text) {
        if (text == null) return false;
        String value = normalize(text);
        if (value.isEmpty()) return false;
        return value.matches("^(nein|falsch|wieder falsch|nicht so|doch|stopp|stop)[ !?,.:;-]*.*")
                || value.startsWith("ich meinte ")
                || value.startsWith("ich meine ")
                || value.startsWith("gemeint war ")
                || value.startsWith("stattdessen ")
                || value.startsWith("sondern ")
                || value.contains("nicht das sondern")
                || value.contains("nicht das, sondern")
                || value.contains("genau andersrum")
                || value.contains("genau anders herum");
    }

    static String instructionSuffix(String userText) {
        boolean correction = looksLikeCorrection(userText);
        boolean followUp = looksLikeFollowUp(userText);
        String continuity;
        if (correction) {
            continuity = "Die aktuelle Nachricht korrigiert oder widerspricht einer früheren Annahme. Behandle die neueste Nutzerkorrektur als verbindlich und setze den alten, widersprochenen Plan nicht fort. Löse Verweise aus dem unmittelbar vorherigen Gespräch auf. ";
        } else if (followUp) {
            continuity = "Die aktuelle Nachricht wirkt wie eine Fortsetzung. Löse Pronomen, kurze Handlungswörter und Verweise zuerst aus dem unmittelbar vorherigen relevanten Gespräch auf. Frage nicht nach Informationen, die dort bereits eindeutig stehen. ";
        } else {
            continuity = "Nutze frühere Gesprächszüge nur, wenn sie für die aktuelle Absicht wirklich relevant sind. Ziehe ein altes Thema nicht in einen klar neuen Auftrag hinein. ";
        }
        return "\nGesprächslogik: " + continuity
                + "Beantworte Folgefragen auf Basis des bereits Gesagten, statt die vorige Antwort vollständig zu wiederholen. "
                + "Bei 'weiter', 'los', 'mach weiter' oder ähnlich kurzen Handlungsfortsetzungen führe den zuletzt eindeutig begonnenen Auftrag fort. "
                + "Wenn sich seit der letzten Antwort nur ein Detail geändert hat, nenne vor allem dieses neue Detail. "
                + "Wenn Yahya etwas korrigiert, hat die neueste Korrektur Vorrang vor älteren Annahmen, Plänen und Zwischenständen. "
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

    private static int findTopicalAnchor(List<Turn> turns, String userText, int currentStart) {
        Set<String> currentTerms = topicTerms(userText);
        if (currentTerms.isEmpty()) return -1;
        int floor = Math.max(0, turns.size() - TOPICAL_LOOKBACK_TURNS);
        int best = -1;
        int bestScore = 0;
        for (int i = turns.size() - 1; i >= floor; i--) {
            Turn turn = turns.get(i);
            if (!"user".equals(turn.role) || turn.content.trim().isEmpty()) continue;
            Set<String> priorTerms = topicTerms(turn.content);
            int score = overlap(currentTerms, priorTerms);
            if (score > bestScore) {
                bestScore = score;
                best = i;
                if (score >= Math.min(2, currentTerms.size())) break;
            }
        }
        return bestScore > 0 && best < currentStart ? best : -1;
    }

    private static boolean fitsWindow(List<Turn> turns, int start, int charBudget, int maxTurns) {
        int chars = 0;
        int count = 0;
        for (int i = Math.max(0, start); i < turns.size(); i++) {
            Turn turn = turns.get(i);
            chars += turn.role.length() + turn.content.length() + 16;
            count++;
            if (count > maxTurns || chars > charBudget) return false;
        }
        return true;
    }

    private static Set<String> topicTerms(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.GERMANY), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9äöüß]+", " ");
        for (String token : normalized.trim().split("\\s+")) {
            if (token.length() < 4 || STOP_WORDS.contains(token)) continue;
            out.add(token);
        }
        return out;
    }

    private static int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String value : left) if (right.contains(value)) count++;
        return count;
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase(Locale.GERMANY).replaceAll("\\s+", " ");
    }
}
