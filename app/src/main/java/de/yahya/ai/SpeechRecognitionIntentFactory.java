package de.yahya.ai;

import android.content.Intent;
import android.speech.RecognizerIntent;

/** Central source for Celin's default speech-recognition language. */
public final class SpeechRecognitionIntentFactory {
    public static final String DEFAULT_LANGUAGE = "de-DE";

    private SpeechRecognitionIntentFactory() {}

    public static Intent createGermanRecognitionIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, DEFAULT_LANGUAGE);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, DEFAULT_LANGUAGE);
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, DEFAULT_LANGUAGE);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich auf Deutsch mit Celin");
        return intent;
    }
}
