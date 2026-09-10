package de.yahya.ai;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * G1.5 protected at-rest persistence for Celine memory.
 *
 * Plain memory text is never written by this owner. Values are encrypted with an
 * AndroidKeyStore AES key and bound to the SharedPreferences key through GCM AAD.
 */
final class CelineProtectedMemoryStorage {
    static final String KEY_ALIAS = "yahya_ai.celine.memory.g1_5.aes";
    static final String ENVELOPE_PREFIX = "gcm1:";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    private final SharedPreferences prefs;
    private volatile SecretKey cachedKey;

    CelineProtectedMemoryStorage(SharedPreferences prefs) {
        if (prefs == null) throw new IllegalArgumentException("prefs must not be null");
        this.prefs = prefs;
    }

    boolean available() {
        try {
            return key() != null;
        } catch (Exception unavailable) {
            return false;
        }
    }

    boolean hasCiphertext(String prefKey) {
        String value = prefs.getString(prefKey, "");
        return value != null && !value.trim().isEmpty();
    }

    String rawCiphertext(String prefKey) {
        String value = prefs.getString(prefKey, "");
        return value == null ? "" : value;
    }

    String read(String prefKey) throws Exception {
        String envelope = rawCiphertext(prefKey);
        if (envelope.trim().isEmpty()) return "";
        if (!envelope.startsWith(ENVELOPE_PREFIX)) {
            throw new IllegalStateException("unsupported protected-memory envelope");
        }
        String[] parts = envelope.substring(ENVELOPE_PREFIX.length()).split(":", -1);
        if (parts.length != 2) throw new IllegalStateException("invalid protected-memory envelope");
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
        if (iv.length == 0 || ciphertext.length == 0) {
            throw new IllegalStateException("invalid protected-memory payload");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(prefKey.getBytes(StandardCharsets.UTF_8));
        byte[] clear = cipher.doFinal(ciphertext);
        return new String(clear, StandardCharsets.UTF_8);
    }

    boolean write(String prefKey, String plaintext, boolean synchronous) {
        if (prefKey == null || prefKey.trim().isEmpty()) return false;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            // AndroidKeyStore owns the random GCM IV when randomized encryption is required.
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(prefKey.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal((plaintext == null ? "" : plaintext)
                    .getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length == 0) return false;
            String envelope = ENVELOPE_PREFIX
                    + Base64.getEncoder().withoutPadding().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().withoutPadding().encodeToString(encrypted);
            SharedPreferences.Editor editor = prefs.edit().putString(prefKey, envelope);
            if (synchronous) return editor.commit();
            editor.apply();
            return true;
        } catch (Exception unavailable) {
            // Fail closed for privacy: never fall back to plaintext persistence.
            return false;
        }
    }

    void remove(String... prefKeys) {
        SharedPreferences.Editor editor = prefs.edit();
        if (prefKeys != null) {
            for (String key : prefKeys) if (key != null && !key.isEmpty()) editor.remove(key);
        }
        editor.apply();
    }

    void destroyKey() {
        cachedKey = null;
        try {
            KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // Stored ciphertext is removed by the caller first. Key deletion is best effort.
        }
    }

    private synchronized SecretKey key() throws Exception {
        if (cachedKey != null) return cachedKey;
        KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
        store.load(null);
        Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            cachedKey = (SecretKey) existing;
            return cachedKey;
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        cachedKey = generator.generateKey();
        return cachedKey;
    }
}
