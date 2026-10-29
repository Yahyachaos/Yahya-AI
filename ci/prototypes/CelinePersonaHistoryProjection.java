package de.yahya.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Non-runtime Core prototype: project event-marked visible chat into provider context.
 *
 * Markers MUST come from the app's actual direct-user command dispatch, never
 * from reinterpreting old text, model/tool text or substrings. Mark the paired
 * app-authored persona acknowledgement as a control event too. This does not
 * own UI history, app wiring, memory, tools or persistence.
 */
public final class CelinePersonaHistoryProjection {
    public static final class Turn {
        public final String role;
        public final String content;
        public final boolean personaControl;

        public Turn(String role, String content, boolean personaControl) {
            this.role = Objects.requireNonNull(role, "role");
            this.content = Objects.requireNonNull(content, "content");
            this.personaControl = personaControl;
        }
    }

    private CelinePersonaHistoryProjection() {}

    /** Preserve order and identity for ordinary events; never mutate UI history. */
    public static List<Turn> providerTurns(List<Turn> visibleHistory) {
        Objects.requireNonNull(visibleHistory, "visibleHistory");
        List<Turn> filtered = new ArrayList<>(visibleHistory.size());
        for (Turn turn : visibleHistory) {
            Objects.requireNonNull(turn, "turn");
            if (!turn.personaControl) filtered.add(turn);
        }
        return Collections.unmodifiableList(filtered);
    }
}
