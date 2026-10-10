package de.yahya.ai;

import java.util.Objects;

/**
 * Ownership-safe prompt-composition seam for the adult persona prototype.
 *
 * <p>This adapter deliberately has no model, memory, tool, persistence, room, or Android
 * authority. In normal mode it returns the exact normal-instructions object unchanged.
 * When persona mode is active it appends only the persona contribution produced by
 * {@link CelinePersonaMode}.</p>
 */
public final class CelinePersonaPromptAdapter {
    private CelinePersonaPromptAdapter() {}

    public static String compose(String normalInstructions, CelinePersonaMode personaMode) {
        Objects.requireNonNull(normalInstructions, "normalInstructions");
        Objects.requireNonNull(personaMode, "personaMode");

        String contribution = personaMode.promptContribution();
        if (contribution.isEmpty()) {
            return normalInstructions;
        }
        if (normalInstructions.isEmpty()) {
            return contribution;
        }
        return normalInstructions + "\n\n" + contribution;
    }
}
