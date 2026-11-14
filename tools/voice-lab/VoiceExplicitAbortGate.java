/**
 * FUTURE-ONLY human-action gate for discarding an unverified speech session.
 * This does NOT establish which old words were actually audible, nor that any
 * physical Android audio track was stopped. Both external inputs must later
 * be enforced by a real serialized post-v83 device adapter.
 */
public final class VoiceExplicitAbortGate {
    public enum UserAction { ABANDON_UNKNOWN_SPEECH_AND_START_NEW_TEXT }
    public enum OldOutputStop { EXTERNALLY_CONFIRMED_STOPPED }
    public enum PriorState { RAW_DRAINED_SPEAKER_UNPROVEN, INTERRUPTED_UNCERTAIN,
        SUSPENDED_BEFORE_OUTPUT, OTHER }

    private VoiceExplicitAbortGate() { }

    /**
     * Pure admission check; returns false unless there is an explicit action,
     * external old-output stop claim, exact old epoch, and NO active dispatch.
     * It does not treat RAW-drained text as externally heard or grant a speaker
     * tail permit. Claims are not measured/proven by this Java-only contract.
     */
    public static boolean mayAbandon(long expectedGeneration, long currentGeneration,
            boolean activeDispatch, PriorState state, UserAction userAction,
            OldOutputStop oldOutputStop) {
        return expectedGeneration > 0 && expectedGeneration == currentGeneration
                && !activeDispatch && state != null && state != PriorState.OTHER
                && userAction == UserAction.ABANDON_UNKNOWN_SPEECH_AND_START_NEW_TEXT
                && oldOutputStop == OldOutputStop.EXTERNALLY_CONFIRMED_STOPPED;
    }
}
