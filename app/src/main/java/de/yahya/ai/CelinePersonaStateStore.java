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
 * Dedicated app-owned persistence for an explicit adult persona opt-in.
 *
 * <p>Independent of Celine's structured G1 memory and conversations. An active
 * snapshot is stored only as AndroidKeyStore AES-GCM ciphertext with per-key
 * authenticated data. An absent/invalid/unreadable record always restores the
 * normal, inactive mode; no cloud/model/tool text can become persona state.</p>
 */
public final class CelinePersonaStateStore {
    static final String PREF_KEY = "celine_persona_state_cipher_v1";
    static final String KEY_ALIAS = "yahya_ai.celine.persona.v1.aes";
    static final String ENVELOPE_PREFIX = "gcm1:";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int MAX_ENVELOPE_CHARS = 256;

    private final SharedPreferences prefs;
    private SecretKey cachedKey;

    public CelinePersonaStateStore(SharedPreferences prefs) {
        if (prefs == null) throw new IllegalArgumentException("prefs must not be null");
        this.prefs = prefs;
    }

    /**
     * Returns true only for an absent or valid encrypted record. Invalid data
     * fails closed; callers may warn the user, but MUST NOT infer activation.
     */
    public synchronized boolean restoreInto(CelinePersonaMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        mode.reset();
        try {
            String envelope = prefs.getString(PREF_KEY, "");
            if (envelope == null || envelope.isEmpty()) return true;
            if (!envelope.startsWith(ENVELOPE_PREFIX) || envelope.length() > MAX_ENVELOPE_CHARS) {
                return false;
            }
            String[] parts = envelope.substring(ENVELOPE_PREFIX.length()).split(":", -1);
            if (parts.length != 2) return false;
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            if (iv.length != IV_BYTES || encrypted.length < TAG_BITS / 8 || encrypted.length > 96) {
                return false;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(PREF_KEY.getBytes(StandardCharsets.UTF_8));
            String decoded = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            // Exactly one schema and exactly one bounded intensity; no persisted
            // instruction, historic command, activation provenance or free text.
            if (!decoded.matches("v1\\|[123]")) return false;
            return mode.restorePersistedState(true, decoded.charAt(3) - '0');
        } catch (Exception unavailableOrTampered) {
            mode.reset();
            return false;
        }
    }

    /**
     * Commit synchronously so a successful activation is not acknowledged as
     * durable before encrypted storage has accepted it. No plaintext fallback.
     */
    public synchronized boolean save(CelinePersonaMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        CelinePersonaMode.State snapshot = mode.snapshot();
        if (!snapshot.active()) return clear();
        if (snapshot.intensity() < CelinePersonaMode.MIN_INTENSITY
                || snapshot.intensity() > CelinePersonaMode.MAX_INTENSITY
                || snapshot.activationSource() == CelinePersonaMode.ActivationSource.NONE) {
            return false;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            cipher.updateAAD(PREF_KEY.getBytes(StandardCharsets.UTF_8));
            String payload = "v1|" + snapshot.intensity();
            byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != IV_BYTES) return false;
            String envelope = ENVELOPE_PREFIX
                    + Base64.getEncoder().withoutPadding().encodeToString(iv)
                    + ":" + Base64.getEncoder().withoutPadding().encodeToString(ciphertext);
            return prefs.edit().putString(PREF_KEY, envelope).commit();
        } catch (Exception unavailable) {
            return false;
        }
    }

    /**
     * An explicit exit deletes the stored active snapshot rather than storing
     * an encrypted "inactive" state. On a failed commit also invalidate this
     * dedicated KeyStore key as a best-effort safeguard against old ciphertext.
     */
    public synchronized boolean clear() {
        try {
            if (prefs.edit().remove(PREF_KEY).commit() && !prefs.contains(PREF_KEY)) return true;
        } catch (Exception ignored) {
            // Revoke below. Never replace the encrypted record with plaintext.
        }
        invalidateDedicatedKey();
        return false;
    }

    private void invalidateDedicatedKey() {
        cachedKey = null;
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // The caller observes failure and must not claim durable deactivation.
        }
    }

    private SecretKey key() throws Exception {
        if (cachedKey != null) return cachedKey;
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            cachedKey = (SecretKey) existing;
            return cachedKey;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
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
