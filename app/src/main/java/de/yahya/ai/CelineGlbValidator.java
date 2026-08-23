package de.yahya.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Fast structural validation for a Celine GLB before Filament ever sees it.
 *
 * Yahya AI supports two compatible levels:
 *  1) the Meshy body rig (real 3D body/head motion), and
 *  2) the production facial rig with the seven ordered morph targets used for lip sync/blinks.
 *
 * Keeping the rig checks here prevents arbitrary/corrupt GLBs from reaching Filament while still
 * allowing the already-created Meshy Celine model to be used immediately as the 3D body fallback.
 */
public final class CelineGlbValidator {
    private static final int GLB_MAGIC = 0x46546C67;      // glTF
    private static final int JSON_CHUNK = 0x4E4F534A;     // JSON
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;

    private static final String[] REQUIRED_MORPHS = {
            "jawOpen",
            "mouthWide",
            "mouthRound",
            "mouthLabial",
            "blinkLeft",
            "blinkRight",
            "smile"
    };

    private static final String[] REQUIRED_RIG_NAMES = {
            "char1",
            "Head",
            "neck",
            "Spine",
            "Spine01",
            "Spine02"
    };

    private CelineGlbValidator() {}

    /**
     * Validates that the file is safe and compatible with Celine's renderer.
     *
     * @return true when the exact production facial morph layout is present; false when this is a
     *         compatible body-rig-only model (for example the current Meshy export).
     */
    public static boolean requireProductionCeline(File file) throws Exception {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Die ausgewählte 3D-Datei wurde nicht gefunden.");
        }
        if (file.length() < 100_000L) {
            throw new IllegalArgumentException("Die gefundene GLB-Datei ist unerwartet klein oder unvollständig.");
        }

        JSONObject json = readGlbJson(file);

        JSONArray skins = json.optJSONArray("skins");
        if (skins == null || skins.length() == 0) {
            throw incompatible("Das Modell besitzt kein verwendbares Skelett/Rig.");
        }

        Set<String> names = new HashSet<>();
        collectNameValues(json, names);
        for (String required : REQUIRED_RIG_NAMES) {
            if (!containsIgnoreCase(names, required)) {
                throw incompatible("Im Rig fehlt „" + required + "“.");
            }
        }

        TargetScan targets = new TargetScan();
        scanTargetNameArrays(json, targets);
        return targets.foundExactProductionOrder;
    }

    private static JSONObject readGlbJson(File file) throws Exception {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(file))) {
            int magic = readLeInt(raw);
            int version = readLeInt(raw);
            long declaredLength = Integer.toUnsignedLong(readLeInt(raw));

            if (magic != GLB_MAGIC) {
                throw new IllegalArgumentException("Die ausgewählte Datei ist keine gültige GLB-Datei.");
            }
            if (version != 2) {
                throw new IllegalArgumentException("Nur GLB/glTF 2.0 wird unterstützt.");
            }
            if (declaredLength > file.length() || declaredLength < 20L) {
                throw new IllegalArgumentException("Die GLB-Datei ist beschädigt oder unvollständig.");
            }

            long consumed = 12L;
            while (consumed + 8L <= declaredLength) {
                int chunkLengthSigned = readLeInt(raw);
                int chunkType = readLeInt(raw);
                long chunkLength = Integer.toUnsignedLong(chunkLengthSigned);
                consumed += 8L;

                if (chunkLength > declaredLength - consumed) {
                    throw new IllegalArgumentException("Die GLB-Datei enthält einen beschädigten Datenblock.");
                }

                if (chunkType == JSON_CHUNK) {
                    if (chunkLength <= 0L || chunkLength > MAX_JSON_BYTES) {
                        throw new IllegalArgumentException("Die GLB-Metadaten sind ungültig oder ungewöhnlich groß.");
                    }
                    byte[] data = new byte[(int) chunkLength];
                    readFully(raw, data);
                    String text = new String(data, StandardCharsets.UTF_8).trim();
                    return new JSONObject(text);
                }

                skipFully(raw, chunkLength);
                consumed += chunkLength;
            }
        }
        throw new IllegalArgumentException("Die GLB-Datei enthält keine lesbaren glTF-Metadaten.");
    }

    private static void collectNameValues(Object value, Set<String> out) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                if ("name".equalsIgnoreCase(key) && child instanceof String) {
                    out.add((String) child);
                }
                if (child != null && child != JSONObject.NULL) collectNameValues(child, out);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Object child = array.opt(i);
                if (child != null && child != JSONObject.NULL) collectNameValues(child, out);
            }
        }
    }

    private static void scanTargetNameArrays(Object value, TargetScan scan) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (child instanceof JSONArray && normalized.contains("targetnames")) {
                    scan.foundAnyTargetArray = true;
                    if (matchesProductionOrder((JSONArray) child)) {
                        scan.foundExactProductionOrder = true;
                    }
                }
                if (child != null && child != JSONObject.NULL) scanTargetNameArrays(child, scan);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Object child = array.opt(i);
                if (child != null && child != JSONObject.NULL) scanTargetNameArrays(child, scan);
            }
        }
    }

    private static boolean matchesProductionOrder(JSONArray names) {
        if (names.length() < REQUIRED_MORPHS.length) return false;
        for (int i = 0; i < REQUIRED_MORPHS.length; i++) {
            String actual = names.optString(i, "");
            if (!REQUIRED_MORPHS[i].equalsIgnoreCase(actual)) return false;
        }
        return true;
    }

    private static boolean containsIgnoreCase(Set<String> values, String wanted) {
        for (String value : values) {
            if (wanted.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static IllegalArgumentException incompatible(String detail) {
        return new IllegalArgumentException(
                detail + " Benötigt wird mindestens Celines Meshy-Körper-Rig mit char1, Head, neck, " +
                        "Spine, Spine01 und Spine02. Für vollständige Lippen- und Blinzelanimationen " +
                        "wird zusätzlich celine_facial_v1.glb mit sieben Gesichts-Morphs verwendet."
        );
    }

    private static int readLeInt(InputStream in) throws Exception {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) throw new EOFException("Unerwartetes Dateiende.");
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static void readFully(InputStream in, byte[] data) throws Exception {
        int off = 0;
        while (off < data.length) {
            int n = in.read(data, off, data.length - off);
            if (n < 0) throw new EOFException("Unerwartetes Dateiende.");
            off += n;
        }
    }

    private static void skipFully(InputStream in, long bytes) throws Exception {
        long remaining = bytes;
        while (remaining > 0L) {
            long skipped = in.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }
            if (in.read() < 0) throw new EOFException("Unerwartetes Dateiende.");
            remaining--;
        }
    }

    private static final class TargetScan {
        boolean foundAnyTargetArray;
        boolean foundExactProductionOrder;
    }
}
