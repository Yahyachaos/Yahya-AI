package de.yahya.ai;

import java.util.Objects;

/**
 * Versioned, deterministic persistence payload codec for the isolated persona policy.
 * This class performs no storage I/O and owns no app, memory, tool or provider state.
 * A later app-owned store may choose to use it after an explicit integration handoff.
 */
public final class CelinePersonaStateCodec {
    private static final String VERSION = "cpm1";
    private static final String OFF = VERSION + "|0|0";

    private CelinePersonaStateCodec() {}

    /** A snapshot is read atomically; activation provenance is never persisted. */
    public static String encode(CelinePersonaMode mode) {
        CelinePersonaMode.State state = Objects.requireNonNull(mode, "mode").snapshot();
        if (!state.active()) {
            return OFF;
        }
        if (state.intensity() < CelinePersonaMode.MIN_INTENSITY
                || state.intensity() > CelinePersonaMode.MAX_INTENSITY) {
            return OFF;
        }
        return VERSION + "|1|" + state.intensity();
    }

    /**
     * Restores a strictly versioned and bounded payload. Missing, corrupt and
     * future-schema values reset to OFF, never to an inferred active state.
     * A successful active restore receives PERSISTED_STATE provenance from the policy.
     */
    public static boolean decodeInto(CelinePersonaMode mode, String value) {
        Objects.requireNonNull(mode, "mode");
        if (OFF.equals(value)) {
            return mode.restorePersistedState(false, 0);
        }
        if (value != null && value.length() == 8 && value.startsWith(VERSION + "|1|")) {
            char digit = value.charAt(7);
            if (digit >= '1' && digit <= '3') {
                return mode.restorePersistedState(true, digit - '0');
            }
        }
        mode.reset();
        return false;
    }
}
