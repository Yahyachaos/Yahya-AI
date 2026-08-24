package de.yahya.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * v38 texture rescue for the verified Meshy GLB.
 *
 * The device trace proves that Filament loads and renders the mesh, but the screenshot proves that
 * the embedded base-color atlas is not visibly contributing to the final material. Meshy's export
 * contains the same embedded PNG in two texture slots (baseColor + emissive). Earlier repairs
 * deliberately removed emissive; v38 restores a low-strength emissive use of the SAME atlas while
 * keeping physically sane non-metallic PBR values. This makes the real skin/hair/clothes atlas
 * visible without reverting to Meshy's fully white emissive defaults.
 */
final class CelineTextureRescue {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK = 0x4E4F534A;

    private CelineTextureRescue() {}

    static boolean apply(Context context) {
        if (context == null) return false;
        File model = Celine3DView.importedModelFile(context);
        if (!model.isFile() || model.length() < 64) return false;
        try {
            byte[] original = readAll(model);
            byte[] rescued = rescue(original);
            if (rescued == original) return false;
            boolean ok = replaceAtomically(model, rescued);
            if (ok) {
                Celine3DDiagnostics.record(context, "V38-100", "GLB-Textur-Rettung gespeichert",
                        "BaseColor + Emissive nutzen eingebettete Meshy-PNG · bytes=" + rescued.length);
            }
            return ok;
        } catch (Throwable e) {
            Celine3DDiagnostics.error(context, "V38-199", "GLB-Textur-Rettung FEHLER", e);
            return false;
        }
    }

    private static byte[] rescue(byte[] original) throws Exception {
        if (original == null || original.length < 28) return original;
        ByteBuffer h = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);
        if (h.getInt(0) != GLB_MAGIC || h.getInt(4) != 2) return original;
        int declaredLength = h.getInt(8);
        int jsonLength = h.getInt(12);
        int jsonType = h.getInt(16);
        if (jsonType != JSON_CHUNK || jsonLength <= 0 || declaredLength > original.length || 20L + jsonLength > declaredLength) {
            return original;
        }

        String jsonText = new String(original, 20, jsonLength, StandardCharsets.UTF_8).trim();
        JSONObject root = new JSONObject(jsonText);
        JSONArray materials = root.optJSONArray("materials");
        JSONArray textures = root.optJSONArray("textures");
        JSONArray images = root.optJSONArray("images");
        if (materials == null || materials.length() == 0 || textures == null || textures.length() == 0 ||
                images == null || images.length() == 0) return original;

        boolean changed = false;
        for (int i = 0; i < materials.length(); i++) {
            JSONObject m = materials.optJSONObject(i);
            if (m == null) continue;

            JSONObject pbr = m.optJSONObject("pbrMetallicRoughness");
            if (pbr == null) {
                pbr = new JSONObject();
                m.put("pbrMetallicRoughness", pbr);
                changed = true;
            }

            JSONObject baseTex = pbr.optJSONObject("baseColorTexture");
            if (baseTex == null || baseTex.optInt("index", -1) != 0) {
                baseTex = new JSONObject();
                baseTex.put("index", 0);
                pbr.put("baseColorTexture", baseTex);
                changed = true;
            }
            if (!isRgba(pbr.optJSONArray("baseColorFactor"), 1, 1, 1, 1)) {
                pbr.put("baseColorFactor", rgba(1, 1, 1, 1));
                changed = true;
            }
            if (Math.abs(pbr.optDouble("metallicFactor", 1.0)) > 0.00001) {
                pbr.put("metallicFactor", 0.0);
                changed = true;
            }
            if (Math.abs(pbr.optDouble("roughnessFactor", -1.0) - 0.78) > 0.00001) {
                pbr.put("roughnessFactor", 0.78);
                changed = true;
            }

            JSONObject emissiveTex = m.optJSONObject("emissiveTexture");
            if (emissiveTex == null || emissiveTex.optInt("index", -1) != 0) {
                emissiveTex = new JSONObject();
                emissiveTex.put("index", 0);
                m.put("emissiveTexture", emissiveTex);
                changed = true;
            }
            if (!isRgb(m.optJSONArray("emissiveFactor"), 0.38, 0.38, 0.38)) {
                m.put("emissiveFactor", rgb(0.38, 0.38, 0.38));
                changed = true;
            }

            JSONObject ext = m.optJSONObject("extensions");
            if (ext == null) {
                ext = new JSONObject();
                m.put("extensions", ext);
                changed = true;
            }
            JSONObject spec = ext.optJSONObject("KHR_materials_specular");
            if (spec == null) {
                spec = new JSONObject();
                ext.put("KHR_materials_specular", spec);
                changed = true;
            }
            if (Math.abs(spec.optDouble("specularFactor", -1.0) - 0.18) > 0.00001) {
                spec.put("specularFactor", 0.18);
                changed = true;
            }
            if (!isRgb(spec.optJSONArray("specularColorFactor"), 1, 1, 1)) {
                spec.put("specularColorFactor", rgb(1, 1, 1));
                changed = true;
            }
        }

        if (!changed) return original;
        return rebuild(original, declaredLength, jsonLength, root.toString());
    }

    private static byte[] rebuild(byte[] original, int declaredLength, int oldJsonLength, String jsonText) {
        byte[] json = jsonText.getBytes(StandardCharsets.UTF_8);
        int padded = (json.length + 3) & ~3;
        int remainderOffset = 20 + oldJsonLength;
        int remainderLength = declaredLength - remainderOffset;
        int newLength = 20 + padded + remainderLength;

        ByteBuffer out = ByteBuffer.allocate(newLength).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(GLB_MAGIC);
        out.putInt(2);
        out.putInt(newLength);
        out.putInt(padded);
        out.putInt(JSON_CHUNK);
        out.put(json);
        while (out.position() < 20 + padded) out.put((byte) 0x20);
        out.put(original, remainderOffset, remainderLength);
        return out.array();
    }

    private static boolean replaceAtomically(File target, byte[] data) throws Exception {
        File parent = target.getParentFile();
        if (parent == null) return false;
        File tmp = new File(parent, "celine.v38.tmp");
        File bak = new File(parent, "celine.v38.bak");
        tmp.delete();
        bak.delete();
        try (FileOutputStream out = new FileOutputStream(tmp, false)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
        if (!target.renameTo(bak)) { tmp.delete(); return false; }
        if (!tmp.renameTo(target)) {
            bak.renameTo(target);
            tmp.delete();
            return false;
        }
        bak.delete();
        return true;
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), Integer.MAX_VALUE))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static JSONArray rgb(double r, double g, double b) {
        JSONArray a = new JSONArray();
        a.put(r); a.put(g); a.put(b); return a;
    }

    private static JSONArray rgba(double r, double g, double b, double a0) {
        JSONArray a = rgb(r, g, b); a.put(a0); return a;
    }

    private static boolean isRgb(JSONArray a, double r, double g, double b) {
        if (a == null || a.length() < 3) return false;
        return near(a.optDouble(0), r) && near(a.optDouble(1), g) && near(a.optDouble(2), b);
    }

    private static boolean isRgba(JSONArray a, double r, double g, double b, double alpha) {
        return isRgb(a, r, g, b) && a.length() >= 4 && near(a.optDouble(3), alpha);
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.00001; }
}
