package de.yahya.ai;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Material;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.gltfio.FilamentAsset;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * v43 decisive TRUE-UNLIT probe.
 *
 * The v39 FORCE-C path and the exact same imported GLB / embedded PNG remain the source of truth.
 * Before Filament creates the asset, this class modifies ONLY the disposable celine.glb working
 * copy: every material gets KHR_materials_unlit and KHR_materials_specular is removed. The immutable
 * celine.original.v39.glb snapshot is never touched.
 *
 * After the view exists, v39 still binds the same proven 4096x4096 GPU texture to baseColorMap.
 * This makes the diagnostic useful: compared with v42, the texture bytes, UVs, mesh, rig and forced
 * texture upload stay the same; only the shading model changes from LIT to true glTF UNLIT.
 */
final class CelineTrueUnlitProbeV43 {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final String UNLIT = "KHR_materials_unlit";
    private static final String SPECULAR = "KHR_materials_specular";
    private static final String PREFS = "yahya_ai";
    private static final String V39_LAST_WORKING_SHA = "v39_last_working_sha256";

    private CelineTrueUnlitProbeV43() {}

    static String prepareWorkingModel(Context context) {
        if (context == null) return "context=null";
        File working = Celine3DView.importedModelFile(context);
        if (!working.isFile() || working.length() < 100_000L) {
            Celine3DDiagnostics.record(context, "V43-099", "TRUE-UNLIT nicht vorbereitet",
                    "Arbeitsmodell fehlt: " + working);
            return "kein Modell";
        }

        try {
            byte[] original = readAll(working);
            ByteBuffer bb = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);
            if (original.length < 28 || bb.getInt(0) != GLB_MAGIC || bb.getInt(4) != 2) {
                throw new IllegalArgumentException("Arbeitsmodell ist keine GLB v2");
            }

            int declaredLength = bb.getInt(8);
            int jsonLength = bb.getInt(12);
            int jsonType = bb.getInt(16);
            if (declaredLength < 28 || declaredLength > original.length ||
                    jsonType != JSON_CHUNK || jsonLength <= 0 || 20L + jsonLength > declaredLength) {
                throw new IllegalArgumentException("GLB-Header/JSON-Chunk ist ungültig");
            }

            String jsonText = new String(original, 20, jsonLength, StandardCharsets.UTF_8).trim();
            JSONObject root = new JSONObject(jsonText);
            JSONArray materials = root.optJSONArray("materials");
            if (materials == null || materials.length() == 0) {
                throw new IllegalArgumentException("GLB enthält keine Materialien");
            }

            int unlitAdded = 0;
            int specularRemoved = 0;
            for (int i = 0; i < materials.length(); i++) {
                JSONObject material = materials.optJSONObject(i);
                if (material == null) continue;
                JSONObject extensions = material.optJSONObject("extensions");
                if (extensions == null) {
                    extensions = new JSONObject();
                    material.put("extensions", extensions);
                }
                if (!extensions.has(UNLIT)) {
                    extensions.put(UNLIT, new JSONObject());
                    unlitAdded++;
                }
                if (extensions.has(SPECULAR)) {
                    extensions.remove(SPECULAR);
                    specularRemoved++;
                }
            }

            addUniqueExtension(root, "extensionsUsed", UNLIT);
            removeExtension(root, "extensionsUsed", SPECULAR);
            removeExtension(root, "extensionsRequired", SPECULAR);

            byte[] patched = rebuildGlb(original, declaredLength, jsonLength, root.toString());
            writeAtomically(working, patched);

            // v39 identifies a fresh user import by comparing celine.glb with this SHA. Because v43
            // deliberately changes only the disposable working copy, we must synchronize the marker
            // after our own patch; otherwise the next app restart could mistake v43 for a new import
            // and overwrite the immutable original snapshot.
            String patchedSha = sha256(working);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(V39_LAST_WORKING_SHA, patchedSha)
                    .commit();

            String detail = "materials=" + materials.length() +
                    " · unlitAdded=" + unlitAdded +
                    " · specularRemoved=" + specularRemoved +
                    " · bytes=" + working.length() +
                    " · sha=" + patchedSha.substring(0, Math.min(12, patchedSha.length())) +
                    " · Original-Snapshot unverändert";
            Celine3DDiagnostics.record(context, "V43-100", "TRUE-UNLIT Arbeitsmodell vorbereitet", detail);
            return detail;
        } catch (Throwable e) {
            Celine3DDiagnostics.error(context, "V43-199", "TRUE-UNLIT Vorbereitung FEHLER", e);
            return "FEHLER " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
        }
    }

    /**
     * Runtime audit only. The actual 4096x4096 texture binding is deliberately still done by v39
     * so that v43 changes only the shader model and not the proven texture path.
     */
    static void auditRuntime(View root) {
        if (root == null) return;
        Celine3DView threeD = find3D(root);
        if (threeD == null) return;

        Context context = root.getContext();
        try {
            Field assetField = Celine3DView.class.getDeclaredField("asset");
            assetField.setAccessible(true);
            FilamentAsset asset = (FilamentAsset) assetField.get(threeD);
            if (asset == null || asset.getInstance() == null) return;

            MaterialInstance[] instances = asset.getInstance().getMaterialInstances();
            int count = instances == null ? 0 : instances.length;
            int baseColorSamplers = 0;
            int specularStrengthParams = 0;
            int emissiveSamplers = 0;

            if (instances != null) {
                for (MaterialInstance mi : instances) {
                    if (mi == null) continue;
                    Material m = mi.getMaterial();
                    if (m == null) continue;
                    if (m.hasParameter("baseColorMap")) baseColorSamplers++;
                    if (m.hasParameter("specularStrength")) specularStrengthParams++;
                    if (m.hasParameter("emissiveMap")) emissiveSamplers++;
                }
            }

            String detail = "materials=" + count +
                    " · baseColorMap=" + baseColorSamplers +
                    " · specularStrengthParam=" + specularStrengthParams +
                    " · emissiveMapParam=" + emissiveSamplers +
                    " · FORCE-C BaseColor bleibt aktiv";
            Celine3DDiagnostics.record(context, "V43-150", "TRUE_UNLIT AKTIV", detail);
        } catch (Throwable e) {
            Celine3DDiagnostics.error(context, "V43-299", "TRUE-UNLIT Runtime-Audit FEHLER", e);
        }
    }

    private static void addUniqueExtension(JSONObject root, String key, String extension) throws Exception {
        JSONArray array = root.optJSONArray(key);
        if (array == null) {
            array = new JSONArray();
            root.put(key, array);
        }
        for (int i = 0; i < array.length(); i++) {
            if (extension.equals(array.optString(i))) return;
        }
        array.put(extension);
    }

    private static void removeExtension(JSONObject root, String key, String extension) throws Exception {
        JSONArray array = root.optJSONArray(key);
        if (array == null) return;
        JSONArray cleaned = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!extension.equals(value)) cleaned.put(value);
        }
        if (cleaned.length() == 0) root.remove(key);
        else root.put(key, cleaned);
    }

    private static byte[] rebuildGlb(byte[] original, int declaredLength, int oldJsonLength,
                                     String newJson) {
        byte[] json = newJson.getBytes(StandardCharsets.UTF_8);
        int paddedJsonLength = (json.length + 3) & ~3;
        int remainderOffset = 20 + oldJsonLength;
        int remainderLength = declaredLength - remainderOffset;
        int newLength = 20 + paddedJsonLength + remainderLength;

        ByteBuffer out = ByteBuffer.allocate(newLength).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(GLB_MAGIC);
        out.putInt(2);
        out.putInt(newLength);
        out.putInt(paddedJsonLength);
        out.putInt(JSON_CHUNK);
        out.put(json);
        while (out.position() < 20 + paddedJsonLength) out.put((byte) 0x20);
        out.put(original, remainderOffset, remainderLength);
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

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) digest.update(buffer, 0, n);
        }
        byte[] hash = digest.digest();
        StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte b : hash) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static void writeAtomically(File target, byte[] data) throws Exception {
        File parent = target.getParentFile();
        if (parent == null) throw new IllegalStateException("Modellordner fehlt");
        File temp = new File(parent, target.getName() + ".v43.tmp");
        File backup = new File(parent, target.getName() + ".v43.bak");
        if (temp.exists() && !temp.delete()) {
            throw new IllegalStateException("Alte v43-Tempdatei kann nicht gelöscht werden");
        }
        if (backup.exists() && !backup.delete()) {
            throw new IllegalStateException("Altes v43-Backup kann nicht gelöscht werden");
        }

        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
        if (!temp.isFile() || temp.length() < 100_000L) {
            temp.delete();
            throw new IllegalStateException("v43-Arbeitskopie ist ungültig");
        }

        if (target.exists() && !target.renameTo(backup)) {
            temp.delete();
            throw new IllegalStateException("Arbeitskopie konnte nicht gesichert werden");
        }
        if (!temp.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target);
            temp.delete();
            throw new IllegalStateException("v43-Arbeitskopie konnte nicht aktiviert werden");
        }
        if (backup.exists()) backup.delete();
    }

    private static Celine3DView find3D(View view) {
        if (view instanceof Celine3DView) return (Celine3DView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Celine3DView found = find3D(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
