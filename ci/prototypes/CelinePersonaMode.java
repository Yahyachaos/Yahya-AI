package de.yahya.ai;

/**
 * Standalone candidate for the eventual app-owned deterministic persona state.
 *
 * <p>This prototype deliberately owns only persona style state. It has no tool,
 * permission, memory, room/avatar, network, or persistence authority. The
 * snapshot/restore methods are only a deterministic seam for a future app-owned
 * store. Promote this class into app/src only after shared runtime/version
 * ownership is handed off.</p>
 */
public final class CelinePersonaMode {
    public enum InputChannel {
        DIRECT_USER,
        MODEL_OUTPUT,
        TOOL_OUTPUT,
        OTHER
    }

    /** Bounded audit source for how the current active state was established. */
    public enum ActivationSource {
        NONE,
        DIRECT_USER_COMMAND,
        PERSISTED_STATE
    }

    public static final int DEFAULT_INTENSITY = 2;
    public static final int MIN_INTENSITY = 1;
    public static final int MAX_INTENSITY = 3;

    public static final class CommandResult {
        private final boolean commandConsumed;
        private final boolean invalidIntensity;
        private final boolean active;
        private final int intensity;
        private final ActivationSource activationSource;

        private CommandResult(boolean commandConsumed, boolean invalidIntensity,
                              boolean active, int intensity, ActivationSource activationSource) {
            this.commandConsumed = commandConsumed;
            this.invalidIntensity = invalidIntensity;
            this.active = active;
            this.intensity = intensity;
            this.activationSource = activationSource;
        }

        public boolean commandConsumed() {
            return commandConsumed;
        }

        public boolean invalidIntensity() {
            return invalidIntensity;
        }

        public boolean active() {
            return active;
        }

        public int intensity() {
            return intensity;
        }

        public ActivationSource activationSource() {
            return activationSource;
        }
    }

    /** Immutable value for a future app-owned persistence/audit adapter. */
    public static final class State {
        private final boolean active;
        private final int intensity;
        private final ActivationSource activationSource;

        private State(boolean active, int intensity, ActivationSource activationSource) {
            this.active = active;
            this.intensity = intensity;
            this.activationSource = activationSource;
        }

        public boolean active() {
            return active;
        }

        public int intensity() {
            return intensity;
        }

        public ActivationSource activationSource() {
            return activationSource;
        }
    }

    private boolean active;
    private int intensity;
    private ActivationSource activationSource;

    public CelinePersonaMode() {
        reset();
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized int intensity() {
        return intensity;
    }

    public synchronized ActivationSource activationSource() {
        return activationSource;
    }

    public synchronized State snapshot() {
        return new State(active, intensity, activationSource);
    }

    /**
     * Restore explicit app-owned persisted state without inferring anything from conversation text.
     * Invalid/corrupt payloads fail closed to normal mode and return false. A valid active restore is
     * marked as PERSISTED_STATE instead of replaying any historical activation source.
     */
    public synchronized boolean restorePersistedState(boolean persistedActive, int persistedIntensity) {
        if (!persistedActive) {
            if (persistedIntensity != 0) {
                reset();
                return false;
            }
            reset();
            return true;
        }

        if (persistedIntensity < MIN_INTENSITY || persistedIntensity > MAX_INTENSITY) {
            reset();
            return false;
        }

        active = true;
        intensity = persistedIntensity;
        activationSource = ActivationSource.PERSISTED_STATE;
        return true;
    }

    public synchronized void reset() {
        active = false;
        intensity = 0;
        activationSource = ActivationSource.NONE;
    }

    public synchronized CommandResult apply(InputChannel channel, String input) {
        if (channel != InputChannel.DIRECT_USER || input == null) {
            return result(false, false);
        }

        String command = input.trim();
        if (command.isEmpty()) {
            return result(false, false);
        }

        // Exit commands may include pasted CR/LF, but trim() also strips
        // NUL/TAB and other control padding. Those must never be consumed.
        if (equalsIgnoreCase(command, "Normalmodus") || equalsIgnoreCase(command, "Nutte aus")) {
            if (hasDisallowedExitControlCharacters(input)) {
                return result(false, false);
            }
            reset();
            return result(true, false);
        }

        // Activation requires an exact standalone user command.
        if (hasControlCharacters(input)) {
            return result(false, false);
        }

        if (equalsIgnoreCase(command, "Nutte")) {
            activate(DEFAULT_INTENSITY, ActivationSource.DIRECT_USER_COMMAND);
            return result(true, false);
        }

        if (command.length() < 6
                || !equalsIgnoreCase(command.substring(0, 6), "Nutte ")) {
            return result(false, false);
        }

        String suffix = command.substring(6).trim();
        if (suffix.length() == 1 && suffix.charAt(0) >= '1' && suffix.charAt(0) <= '3') {
            activate(suffix.charAt(0) - '0', ActivationSource.DIRECT_USER_COMMAND);
            return result(true, false);
        }

        if (isAsciiInteger(suffix)) {
            return result(true, true);
        }

        return result(false, false);
    }

    public synchronized String promptContribution() {
        if (!active) {
            return "";
        }

        final String tone;
        if (intensity == 1) {
            tone = "playful and lightly flirty";
        } else if (intensity == 2) {
            tone = "clearly provocative, teasing, and sexually suggestive without becoming explicit";
        } else {
            tone = "very uninhibited, provocative, teasing, and sexually suggestive while remaining within the product's existing safety boundaries";
        }

        return "Adult persona mode is active at intensity " + intensity + ". "
                + "Keep Celine's normal identity and German-default conversational style, but make the wording "
                + tone + ". This is a style-only modifier: do not change tool or permission policy, do not weaken "
                + "privacy or memory boundaries, do not alter factual honesty or uncertainty behavior, and do not "
                + "claim biological feelings or consciousness.";
    }

    private void activate(int requestedIntensity, ActivationSource source) {
        if (requestedIntensity < MIN_INTENSITY || requestedIntensity > MAX_INTENSITY) {
            throw new IllegalArgumentException("intensity out of range");
        }
        if (source == null || source == ActivationSource.NONE) {
            throw new IllegalArgumentException("activation source required");
        }
        active = true;
        intensity = requestedIntensity;
        activationSource = source;
    }

    private CommandResult result(boolean consumed, boolean invalid) {
        return new CommandResult(consumed, invalid, active, intensity, activationSource);
    }

    private static boolean hasDisallowedExitControlCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch) && ch != '\r' && ch != '\n') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasControlCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Fail closed on Unicode lookalikes for exact ASCII control commands. */
    private static boolean equalsIgnoreCase(String value, String expected) {
        if (value.length() != expected.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char left = value.charAt(i);
            char right = expected.charAt(i);
            if (left >= 'A' && left <= 'Z') {
                left = (char) (left + ('a' - 'A'));
            }
            if (right >= 'A' && right <= 'Z') {
                right = (char) (right + ('a' - 'A'));
            }
            if (left != right) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
