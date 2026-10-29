#!/usr/bin/env python3
"""JVM crypto/envelope contract for the real Core Android persona state store.

Only Android framework glue and key provisioning are stubbed. The exercised
AES-GCM encryption/decryption and the promoted CelinePersonaMode/Store sources
are real. This does NOT prove AndroidKeyStore availability, restart or UI wiring.
"""
from __future__ import annotations

import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/de/yahya/ai"

SHARED_PREFS = r'''
package android.content;
public interface SharedPreferences {
    String getString(String key, String fallback);
    boolean contains(String key);
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor remove(String key);
        boolean commit();
    }
}
'''
KEY_PROPERTIES = r'''
package android.security.keystore;
public final class KeyProperties {
    public static final String KEY_ALGORITHM_AES = "AES";
    public static final String BLOCK_MODE_GCM = "GCM";
    public static final String ENCRYPTION_PADDING_NONE = "NoPadding";
    public static final int PURPOSE_ENCRYPT = 1;
    public static final int PURPOSE_DECRYPT = 2;
}
'''
KEY_SPEC = r'''
package android.security.keystore;
public final class KeyGenParameterSpec implements java.security.spec.AlgorithmParameterSpec {
    public static final class Builder {
        public Builder(String alias, int purposes) {}
        public Builder setBlockModes(String... modes) { return this; }
        public Builder setEncryptionPaddings(String... paddings) { return this; }
        public Builder setKeySize(int bits) { return this; }
        public Builder setRandomizedEncryptionRequired(boolean required) { return this; }
        public KeyGenParameterSpec build() { return new KeyGenParameterSpec(); }
    }
}
'''
HARNESS = r'''
package de.yahya.ai;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CelinePersonaStateStoreContractTest {
    static final byte[] KEY = new byte[32];
    static final SecretKeySpec SECRET = new SecretKeySpec(KEY, "AES");
    static int checks;
    static final class MemoryPrefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<>();
        boolean failCommit;
        public String getString(String key, String fallback) {
            Object val = map.get(key);
            if (val == null) return fallback;
            return (String) val;
        }
        public boolean contains(String key) { return map.containsKey(key); }
        public Editor edit() {
            return new Editor() {
                String key, value;
                boolean remove;
                public Editor putString(String k, String v) {
                    key = k; value = v; return this;
                }
                public Editor remove(String k) { key = k; remove = true; return this; }
                public boolean commit() {
                    if (failCommit) return false;
                    if (remove) map.remove(key); else map.put(key, value);
                    return true;
                }
            };
        }
    }
    static void ok(boolean truth, String why) {
        checks++;
        if (!truth) throw new AssertionError(why);
    }
    static CelinePersonaStateStore store(MemoryPrefs prefs) throws Exception {
        CelinePersonaStateStore store = new CelinePersonaStateStore(prefs);
        Field cached = CelinePersonaStateStore.class.getDeclaredField("cachedKey");
        cached.setAccessible(true);
        cached.set(store, SECRET); // fake ONLY AndroidKeyStore provisioning
        return store;
    }
    static CelinePersonaMode activated(int intensity) {
        CelinePersonaMode mode = new CelinePersonaMode();
        CelinePersonaMode.CommandResult result = mode.apply(
            CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte " + intensity);
        ok(result.commandConsumed() && mode.isActive(), "direct opt-in");
        return mode;
    }
    static String encryptedPayload(String plaintext, String aad) throws Exception {
        byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, SECRET, new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return "gcm1:" + Base64.getEncoder().withoutPadding().encodeToString(iv)
            + ":" + Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }
    static void rejects(MemoryPrefs prefs, String value, String why) throws Exception {
        prefs.map.put(CelinePersonaStateStore.PREF_KEY, value);
        CelinePersonaMode mode = activated(2); // restore must first reset a live mode
        ok(!store(prefs).restoreInto(mode), why + " rejected");
        ok(!mode.isActive() && mode.intensity() == 0 &&
           mode.activationSource() == CelinePersonaMode.ActivationSource.NONE,
           why + " fail-closed state");
    }
    public static void main(String[] args) throws Exception {
        MemoryPrefs prefs = new MemoryPrefs();
        CelinePersonaStateStore first = store(prefs);
        CelinePersonaMode mode = activated(1);
        ok(first.restoreInto(mode) && !mode.isActive(), "missing envelope defaults OFF");
        mode = activated(3);
        ok(first.save(mode), "durable encrypted commit succeeds");
        String envelope = prefs.getString(CelinePersonaStateStore.PREF_KEY, "");
        ok(envelope.startsWith("gcm1:") && envelope.length() < 256, "bounded encrypted envelope");
        ok(!envelope.contains("v1|3") && !envelope.contains("Nutte"), "no plaintext state or command");
        ok(!envelope.equals(encryptedPayload("v1|3", CelinePersonaStateStore.PREF_KEY)),
           "randomized encryption");
        CelinePersonaMode fresh = new CelinePersonaMode();
        ok(store(prefs).restoreInto(fresh) && fresh.isActive() && fresh.intensity() == 3 &&
           fresh.activationSource() == CelinePersonaMode.ActivationSource.PERSISTED_STATE,
           "new instance restores bounded opt-in with persisted provenance");
        ok(first.save(mode) && !envelope.equals(prefs.getString(CelinePersonaStateStore.PREF_KEY, "")),
           "same intensity uses fresh GCM nonce");
        String good = prefs.getString(CelinePersonaStateStore.PREF_KEY, "");
        String flipped = good.substring(0, good.length()-5)
            + (good.charAt(good.length()-5) == 'A' ? 'B' : 'A')
            + good.substring(good.length()-4);
        rejects(prefs, flipped, "tampered authentication tag/body");
        rejects(prefs, "plaintext:v1|3", "unencrypted opt-in");
        rejects(prefs, "gcm1:!!!:???", "malformed base64");
        rejects(prefs, "gcm1:AA:AA", "invalid IV/tag lengths");
        rejects(prefs, "gcm1:" + "A".repeat(260), "oversized envelope");
        rejects(prefs, encryptedPayload("v1|0", CelinePersonaStateStore.PREF_KEY),
            "invalid intensity");
        rejects(prefs, encryptedPayload("v1|3:inject", CelinePersonaStateStore.PREF_KEY),
            "injected extra schema fields");
        rejects(prefs, encryptedPayload("v1|2", "different-pref-key"), "wrong AAD");
        prefs.map.put(CelinePersonaStateStore.PREF_KEY, Integer.valueOf(3));
        CelinePersonaMode badType = activated(1);
        ok(!store(prefs).restoreInto(badType) && !badType.isActive(),
           "wrong preference value type fails closed");
        prefs.map.clear();
        mode = activated(2);
        ok(first.save(mode), "resave valid active state");
        mode.apply(CelinePersonaMode.InputChannel.DIRECT_USER, "Nutte aus");
        ok(!mode.isActive(), "immediate direct exit");
        ok(first.save(mode) && !prefs.contains(CelinePersonaStateStore.PREF_KEY),
           "inactive saves delete record");
        ok(store(prefs).restoreInto(new CelinePersonaMode()), "restart remains inactive");
        mode = activated(3);
        prefs.failCommit = true;
        ok(!first.save(mode) && !prefs.contains(CelinePersonaStateStore.PREF_KEY),
           "commit failure cannot claim durable activation");
        prefs.failCommit = false;
        ok(first.save(mode), "after recovery valid activation encrypted");
        prefs.failCommit = true;
        mode.reset();
        ok(!first.save(mode), "failed deletion cannot claim durable deactivation");
        ok(!mode.isActive(), "current process state stays OFF after failed deletion");
        System.out.println("Celine Persona encrypted store contract: PASS (" + checks
            + " assertions, real JCA AES-GCM, exact store and persona sources; "
            + "AndroidKeyStore creation/device integration NOT tested)");
    }
}
'''


def main() -> int:
    sources = [SOURCE / name for name in ("CelinePersonaStateStore.java", "CelinePersonaMode.java")]
    for source in sources:
        if not source.is_file():
            raise SystemExit(f"missing promoted source: {source}")
    with tempfile.TemporaryDirectory(prefix="celine-persona-store-contract-") as tmp:
        out = pathlib.Path(tmp)
        stub_paths = {
            "android/content/SharedPreferences.java": SHARED_PREFS,
            "android/security/keystore/KeyProperties.java": KEY_PROPERTIES,
            "android/security/keystore/KeyGenParameterSpec.java": KEY_SPEC,
            "de/yahya/ai/CelinePersonaStateStoreContractTest.java": HARNESS,
        }
        for relative, source in stub_paths.items():
            file = out / relative
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(source, encoding="utf-8")
        classes = out / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), *map(str, sources),
                        *map(str, (out / name for name in stub_paths))], check=True, cwd=ROOT)
        subprocess.run(["java", "-cp", str(classes),
                        "de.yahya.ai.CelinePersonaStateStoreContractTest"], check=True, cwd=ROOT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
