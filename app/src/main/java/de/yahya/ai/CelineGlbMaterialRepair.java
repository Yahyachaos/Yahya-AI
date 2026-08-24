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
import java.util.HashSet;
import java.util.Set;

/**
 * Normalizes the specific Meshy material exported for Celine before Filament loads it.
 *
 * v32 keeps the proven direct renderer untouched and fixes only the GLB material:
 * BaseColor uses the embedded PNG, the material is non-metallic, roughness is suitable for
 * skin / hair / fabric, emissive is disabled, and the extreme Meshy specular value is clamped.
 */
final class CelineGlbMaterialRepair {
    private static final int GLB_MAGIC = 0x46546C67; // "glTF" little-endian
    private static final int JSON_CHUNK = 0x4E4F534A; // "JSON" little-endian

    private CelineGlbMaterialRepair() {}

    static boolean repairImportedModel(Context context) {
        if (context == null) return false;
        File model = Celine3DView.importedModelFile(context);
        if (!model.isFile() || model.length() < 64) return false;

        try {
            byte[] original = readAll(model);
            byte[] repaired = repairIfNeeded(original);
            if (repaired == original) return false;
            return replaceAtomically(model, repaired);
        } catch (Throwable ignored) {
            // Never risk preventing the renderer from starting. The existing file remains intact.
            return false;
        }
    }

    static byte[] repairIfNeeded(byte[] original) throws Exception {
        if (original == null || original.length < 28) return original;

        ByteBuffer header = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt(0) != GLB_MAGIC || header.getInt(4) != 2) return original;

        int declaredLength = header.getInt(8);
        if (declaredLength < 28 || declaredLength > original.length) return original;

        int jsonLength = header.getInt(12);
        int jsonType = header.getInt(16);
        if (jsonType != JSON_CHUNK || jsonLength <= 0 || 20L + jsonLength > declaredLength) {
            return original;
        }

        String jsonText = new String(original, 20, jsonLength, StandardCharsets.UTF_8).trim();
        JSONObject root = new JSONObject(jsonText);
        if (!isExpectedCelineRig(root)) return original;

        JSONArray materials = root.optJSONArray("materials");
        JSONArray textures = root.optJSONArray("textures");
        JSONArray images = root.optJSONArray("images");
        if (materials == null || materials.length() == 0 || textures == null || textures.length() == 0 ||
                images == null || images.length() != 1) {
            return original;
        }

        boolean changed = false;

        for (int i = 0; i < materials.length(); i++) {
            JSONObject material = materials.optJSONObject(i);
            if (material == null) continue;

            // Meshy exported full emissive white + emissiveTexture. Disable both.
            JSONArray emissive = material.optJSONArray("emissiveFactor");
            if (!isZeroRgb(emissive)) {
                material.put("emissiveFactor", rgb(0.0, 0.0, 0.0));
                changed = true;
            }
            if (material.has("emissiveTexture")) {
                material.remove("emissiveTexture");
                changed = true;
            }

            JSONObject pbr = material.optJSONObject("pbrMetallicRoughness");
            if (pbr == null) {
                pbr = new JSONObject();
                material.put("pbrMetallicRoughness", pbr);
                changed = true;
            }

            // Explicitly use a neutral multiplier for the embedded BaseColor PNG.
            JSONArray baseColorFactor = pbr.optJSONArray("baseColorFactor");
            if (!isRgba(baseColorFactor, 1.0, 1.0, 1.0, 1.0)) {
                pbr.put("baseColorFactor", rgba(1.0, 1.0, 1.0, 1.0));
                changed = true;
            }

            // Critical v32 fix: glTF defaults metallicFactor to 1.0 when omitted.
            // Celine is skin / hair / fabric, not metal.
            if (!pbr.has("metallicFactor") || Math.abs(pbr.optDouble("metallicFactor", 1.0)) > 0.000001) {
                pbr.put("metallicFactor", 0.0);
                changed = true;
            }

            // Match the verified celine_v2.glb material.
            if (!pbr.has("roughnessFactor") || Math.abs(pbr.optDouble("roughnessFactor", -1.0) - 0.72) > 0.000001) {
                pbr.put("roughnessFactor", 0.72);
                changed = true;
            }

            JSONObject baseColor = pbr.optJSONObject("baseColorTexture");
            if (baseColor != null) {
                int currentIndex = baseColor.optInt("index", -1);
                if (currentIndex > 0 && currentIndex < textures.length()) {
                    JSONObject currentTexture = textures.optJSONObject(currentIndex);
                    JSONObject firstTexture = textures.optJSONObject(0);
                    if (currentTexture != null && firstTexture != null) {
                        int currentSource = currentTexture.optInt("source", -1);
                        int firstSource = firstTexture.optInt("source", -2);
                        if (currentSource >= 0 && currentSource == firstSource) {
                            baseColor.put("index", 0);
                            changed = true;
                        }
                    }
                }
            }

            // Meshy emitted KHR_materials_specular.specularColorFactor = [2,2,2].
            // Keep the extension but normalize it to the verified celine_v2 values.
            JSONObject extensions = material.optJSONObject("extensions");
            if (extensions == null) {
                extensions = new JSONObject();
                material.put("extensions", extensions);
                changed = true;
            }
            JSONObject specular = extensions.optJSONObject("KHR_materials_specular");
            if (specular == null) {
                specular = new JSONObject();
                extensions.put("KHR_materials_specular", specular);
                changed = true;
            }
            if (!specular.has("specularFactor") || Math.abs(specular.optDouble("specularFactor", -1.0) - 0.3) > 0.000001) {
                specular.put("specularFactor", 0.3);
                changed = true;
            }
            JSONArray specularColor = specular.optJSONArray("specularColorFactor");
            if (!isRgb(specularColor, 1.0, 1.0, 1.0)) {
                specular.put("specularColorFactor", rgb(1.0, 1.0, 1.0));
                changed = true;
            }
        }

        // Meshy exported duplicate texture records that both reference the same single PNG.
        // Collapse only when every texture truly points at the same source, so unrelated GLBs stay untouched.
        if (textures.length() > 1 && allTexturesUseSameSource(textures)) {
            JSONObject first = textures.optJSONObject(0);
            if (first != null) {
                JSONArray normalized = new JSONArray();
                JSONObject copy = new JSONObject();
                if (first.has("sampler")) copy.put("sampler", first.getInt("sampler"));
                copy.put("source", first.optInt("source", 0));
                normalized.put(copy);
                root.put("textures", normalized);
                changed = true;
            }
        }

        if (!changed) return original;
        return rebuildGlb(original, declaredLength, jsonLength, root.toString());
    }

    private static boolean isExpectedCelineRig(JSONObject root) {
        JSONArray nodes = root.optJSONArray("nodes");
        if (nodes == null) return false;
        Set<String> names = new HashSet<>();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            if (node != null) {
                String name = node.optString("name", "");
                if (!name.isEmpty()) names.add(name);
            }
        }
        return names.contains("char1") && names.contains("Head") && names.contains("neck") &&
                names.contains("Spine") && names.contains("Spine01") && names.contains("Spine02");
    }

    private static boolean allTexturesUseSameSource(JSONArray textures) {
        if (textures == null || textures.length() < 2) return false;
        JSONObject first = textures.optJSONObject(0);
        if (first == null) return false;
        int source = first.optInt("source", -1);
        if (source < 0) return false;
        for (int i = 1; i < textures.length(); i++) {
            JSONObject texture = textures.optJSONObject(i);
            if (texture == null || texture.optInt("source", -2) != source) return false;
        }
        return true;
    }

    private static boolean isZeroRgb(JSONArray value) {
        return isRgb(value, 0.0, 0.0, 0.0);
    }

    private static boolean isRgb(JSONArray value, double r, double g, double b) {
        if (value == null || value.length() < 3) return false;
        return Math.abs(numberAt(value, 0, Double.NaN) - r) < 0.000001 &&
                Math.abs(numberAt(value, 1, Double.NaN) - g) < 0.000001 &&
                Math.abs(numberAt(value, 2, Double.NaN) - b) < 0.000001;
    }

    private static boolean isRgba(JSONArray value, double r, double g, double b, double a) {
        if (value == null || value.length() < 4) return false;
        return isRgb(value, r, g, b) && Math.abs(numberAt(value, 3, Double.NaN) - a) < 0.000001;
    }

    /** Android's bundled org.json does not expose JSONArray.optDouble(index, fallback) on all API levels. */
    private static double numberAt(JSONArray value, int index, double fallback) {
        try {
            Object item = value.get(index);
            if (item instanceof Number) return ((Number) item).doubleValue();
            return Double.parseDouble(String.valueOf(item));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static JSONArray rgb(double r, double g, double b) {
        JSONArray result = new JSONArray();
        try {
            result.put(r);
            result.put(g);
            result.put(b);
        } catch (Throwable impossibleForFiniteRgb) {
            return new JSONArray();
        }
        return result;
    }

    private static JSONArray rgba(double r, double g, double b, double a) {
        JSONArray result = new JSONArray();
        try {
            result.put(r);
            result.put(g);
            result.put(b);
            result.put(a);
        } catch (Throwable impossibleForFiniteRgba) {
            return new JSONArray();
        }
        return result;
    }

    private static byte[] rebuildGlb(byte[] original, int declaredLength, int oldJsonLength,
                                     String normalizedJson) {
        byte[] json = normalizedJson.getBytes(StandardCharsets.UTF_8);
        int paddedJsonLength = (json.length + 3) & ~3;
        int oldRemainderOffset = 20 + oldJsonLength;
        int remainderLength = declaredLength - oldRemainderOffset;
        int newLength = 20 + paddedJsonLength + remainderLength;

        ByteBuffer out = ByteBuffer.allocate(newLength).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(GLB_MAGIC);
        out.putInt(2);
        out.putInt(newLength);
        out.putInt(paddedJsonLength);
        out.putInt(JSON_CHUNK);
        out.put(json);
        while (out.position() < 20 + paddedJsonLength) out.put((byte) 0x20);
        out.put(original, oldRemainderOffset, remainderLength);
        return out.array();
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(file.length(), Integer.MAX_VALUE))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static boolean replaceAtomically(File target, byte[] data) throws Exception {
        File parent = target.getParentFile();
        if (parent == null) return false;
        File temp = new File(parent, target.getName() + ".repair.tmp");
        File backup = new File(parent, target.getName() + ".repair.bak");

        if (temp.exists()) temp.delete();
        if (backup.exists()) backup.delete();

        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }

        if (!target.renameTo(backup)) {
            temp.delete();
            return false;
        }
        if (!temp.renameTo(target)) {
            backup.renameTo(target);
            temp.delete();
            return false;
        }
        backup.delete();
        return true;
    }
}
