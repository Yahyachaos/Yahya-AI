package de.yahya.ai;

/**
 * Central routing policy for Celin's speech output.
 * Keeps engine selection outside MainActivity so a local neural engine can be
 * added later without changing conversation/UI code.
 */
public final class SpeechOutputRouter {
    public enum Engine {
        ANDROID_OFFLINE,
        ONLINE_NEURAL
    }

    private SpeechOutputRouter() {}

    public static Engine select(boolean neuralVoiceEnabled, String apiKey) {
        boolean cloudAvailable = apiKey != null && !apiKey.trim().isEmpty();
        return neuralVoiceEnabled && cloudAvailable
                ? Engine.ONLINE_NEURAL
                : Engine.ANDROID_OFFLINE;
    }
}
