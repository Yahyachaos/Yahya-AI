package de.yahya.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.filament.Engine;
import com.google.android.filament.Material;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.Texture;
import com.google.android.filament.TextureSampler;
import com.google.android.filament.android.TextureHelper;
import com.google.android.filament.gltfio.FilamentAsset;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * v39 controlled texture experiment.
 *
 * A = original imported GLB + original Meshy material factors.
 * B = clean PBR GLB, automatic gltfio baseColor binding.
 * C = clean PBR GLB + explicit GPU upload/binding of the embedded PNG to baseColorMap.
 *
 * The immutable source snapshot lives beside celine.glb as celine.original.v39.glb.
 * celine.glb is only a disposable working copy for the selected mode.
 */
final class CelineTexturePipelineV39 {
    enum Mode { A_ORIGINAL, B_CLEAN_PBR, C_FORCE_TEXTURE }

    private static final String PREFS = "yahya_ai";
    private static final String KEY_MODE = "v39_texture_mode";
    private static final String KEY_LAST_WORKING_SHA = "v39_last_working_sha256";
    private static final String KEY_TEXTURE_STATUS = "v39_texture_status";
    private static final String ORIGINAL_NAME = "celine.original.v39.glb";
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;

    private static final Map<Celine3DView, Texture> FORCED_TEXTURES = new WeakHashMap<>();

    private CelineTexturePipelineV39() {}

    static Mode getMode(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, Mode.B_CLEAN_PBR.name());
        try { return Mode.valueOf(value); }
        catch (Throwable ignored) { return Mode.B_CLEAN_PBR; }
    }

    static void setMode(Context context, Mode mode) {
        if (context == null) return;
        Mode safe = mode == null ? Mode.B_CLEAN_PBR : mode;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODE, safe.name())
                .putString(KEY_TEXTURE_STATUS, "warte")
                .apply();
        Celine3DDiagnostics.record(context, "V39-090", "A/B/C-Modus gewählt", modeLabel(safe));
    }

    static String modeLabel(Mode mode) {
        if (mode == Mode.A_ORIGINAL) return "A · ORIGINAL";
        if (mode == Mode.C_FORCE_TEXTURE) return "C · FORCE TEXTURE";
        return "B · CLEAN PBR";
    }

    static String statusLine(Context context) {
        if (context == null) return "3D-Test: ?";
        String state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TEXTURE_STATUS, "warte");
        return modeLabel(getMode(context)) + " · Textur: " + state;
    }

    static File originalModelFile(Context context) {
        File working = Celine3DView.importedModelFile(context);
        File parent = working.getParentFile();
        return new File(parent, ORIGINAL_NAME);
    }

    /**
     * Runs before MainActivity creates the Filament view.
     * Detects a newly imported celine.glb by SHA, captures it as immutable source, then prepares the
     * disposable working copy for the chosen test mode.
     */
    static String prepareWorkingModel(Context context) {
        if (context == null) return "context=null";
        File working = Celine3DView.importedModelFile(context);
        if (!working.isFile() || working.length() < 100_000L) {
            Celine3DDiagnostics.record(context, "V39-099", "Kein Arbeitsmodell für A/B/C", String.valueOf(working));
            return "kein Modell";
        }

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            File original = originalModelFile(context);
            String currentSha = sha256(working);
            String lastWorkingSha = prefs.getString(KEY_LAST_WORKING_SHA, "");

            boolean freshImport = original.isFile() && !lastWorkingSha.isEmpty() && !currentSha.equals(lastWorkingSha);
            if (!original.isFile() || freshImport) {
                copyFile(working, original);
                Celine3DDiagnostics.record(context,
                        freshImport ? "V39-102" : "V39-101",
                        freshImport ? "Neuen Original-Import eingefroren" : "Original-Snapshot angelegt",
                        "bytes=" + original.length() + " sha=" + shortSha(sha256(original)));
            }

            byte[] source = readAll(original.isFile() ? original : working);
            Mode mode = getMode(context);
            byte[] prepared = source;
            if (mode != Mode.A_ORIGINAL) {
                prepared = CelineGlbMaterialRepair.repairIfNeeded(source);
            }
            writeAtomically(working, prepared);
            String preparedSha = sha256(working);
            prefs.edit()
                    .putString(KEY_LAST_WORKING_SHA, preparedSha)
                    .putString(KEY_TEXTURE_STATUS, mode == Mode.C_FORCE_TEXTURE ? "noch nicht gebunden" : "AUTO")
                    .commit();

            String structure = analyzeGlb(working);
            Celine3DDiagnostics.record(context, "V39-110", "Arbeitsmodell vorbereitet",
                    modeLabel(mode) + " · bytes=" + working.length() + " sha=" + shortSha(preparedSha) + " · " + structure);
            return modeLabel(mode) + " · " + structure;
        } catch (Throwable e) {
            Celine3DDiagnostics.error(context, "V39-199", "A/B/C-Modellvorbereitung FEHLER", e);
            return "FEHLER " + e.getClass().getSimpleName();
        }
    }

    /** Called after Celine3DView exists. Applies only runtime values and, for C, a real GPU texture. */
    static void applyRuntime(View root) {
        if (root == null) return;
        Celine3DView threeD = find3D(root);
        if (threeD == null) return;
        Context context = root.getContext();
        Mode mode = getMode(context);

        try {
            Field engineField = Celine3DView.class.getDeclaredField("engine");
            Field assetField = Celine3DView.class.getDeclaredField("asset");
            engineField.setAccessible(true);
            assetField.setAccessible(true);
            Engine engine = (Engine) engineField.get(threeD);
            FilamentAsset asset = (FilamentAsset) assetField.get(threeD);
            if (engine == null || asset == null || asset.getInstance() == null) return;

            MaterialInstance[] materials = asset.getInstance().getMaterialInstances();
            if (materials == null || materials.length == 0) {
                Celine3DDiagnostics.record(context, "V39-298", "Kein Runtime-Material", "materials=0");
                return;
            }

            List<String> paramSummary = new ArrayList<>();
            boolean samplerPresent = false;
            for (MaterialInstance mi : materials) {
                if (mi == null) continue;
                Material material = mi.getMaterial();
                if (material != null) {
                    StringBuilder names = new StringBuilder();
                    for (Material.Parameter p : material.getParameters()) {
                        if (names.length() > 0) names.append(',');
                        names.append(p.name).append(':').append(p.type.name());
                        if ("baseColorMap".equals(p.name) && p.type == Material.Parameter.Type.SAMPLER_2D) samplerPresent = true;
                    }
                    paramSummary.add(names.toString());
                }

                if (mode == Mode.A_ORIGINAL) {
                    // Recreate Meshy's original factors after v36's safety pass. Texture bindings loaded
                    // from the untouched original GLB remain untouched.
                    set1(mi, "metallicFactor", 1.0f);
                    set1(mi, "roughnessFactor", 1.0f);
                    set4(mi, "baseColorFactor", 1f, 1f, 1f, 1f);
                    set3(mi, "emissiveFactor", 1f, 1f, 1f);
                    set1(mi, "emissiveStrength", 1.0f);
                    set1(mi, "specularFactor", 1.0f);
                    set3(mi, "specularColorFactor", 2f, 2f, 2f);
                } else {
                    applyCleanPbr(mi);
                }
            }

            Celine3DDiagnostics.record(context, "V39-120", "Filament-Materialparameter erfasst",
                    "mode=" + modeLabel(mode) + " · baseColorMapParam=" + samplerPresent + " · " + joinLimited(paramSummary));

            if (mode == Mode.C_FORCE_TEXTURE) {
                BindResult result = bindEmbeddedBaseColor(context, threeD, engine, materials,
                        readSelectedModelBytes(context));
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_TEXTURE_STATUS, result.ok ? "JA (GPU)" : "NEIN")
                        .apply();
                Celine3DDiagnostics.record(context, result.ok ? "V39-150" : "V39-159",
                        result.ok ? "TEXTURE_OK · BaseColor explizit gebunden" : "TEXTURE_BIND_FAILED",
                        result.detail);
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_TEXTURE_STATUS, samplerPresent ? "AUTO (?)" : "NEIN")
                        .apply();
                Celine3DDiagnostics.record(context, samplerPresent ? "V39-140" : "V39-149",
                        samplerPresent ? "BaseColor-Sampler vorhanden · AUTO" : "BaseColor-Sampler FEHLT",
                        modeLabel(mode));
            }
        } catch (Throwable e) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_TEXTURE_STATUS, "FEHLER")
                    .apply();
            Celine3DDiagnostics.error(context, "V39-299", "Runtime-Texturdiagnose FEHLER", e);
        }
    }

    private static void applyCleanPbr(MaterialInstance mi) {
        set1(mi, "metallicFactor", 0.0f);
        set1(mi, "roughnessFactor", 0.72f);
        set4(mi, "baseColorFactor", 1f, 1f, 1f, 1f);
        set3(mi, "emissiveFactor", 0f, 0f, 0f);
        set1(mi, "emissiveStrength", 0.0f);
        set1(mi, "specularFactor", 0.3f);
        set3(mi, "specularColorFactor", 1f, 1f, 1f);
        set1(mi, "reflectance", 0.5f);
    }

    private static BindResult bindEmbeddedBaseColor(Context context, Celine3DView view, Engine engine,
                                                     MaterialInstance[] materials, byte[] glbBytes) {
        try {
            synchronized (FORCED_TEXTURES) {
                Texture existing = FORCED_TEXTURES.get(view);
                if (existing != null) {
                    int count = bindTextureToMaterials(materials, existing);
                    return new BindResult(count > 0, "reuse=true · boundMaterials=" + count);
                }
            }

            ImageBytes image = extractEmbeddedImage(glbBytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.length);
            if (bitmap == null) return new BindResult(false, "BitmapFactory.decodeByteArray=null");
            if (bitmap.getConfig() == Bitmap.Config.HARDWARE) bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);

            Texture texture = new Texture.Builder()
                    .width(bitmap.getWidth())
                    .height(bitmap.getHeight())
                    .levels(1)
                    .sampler(Texture.Sampler.SAMPLER_2D)
                    .format(Texture.InternalFormat.SRGB8_A8)
                    .build(engine);
            TextureHelper.setBitmap(engine, texture, 0, bitmap);
            engine.flushAndWait();
            int bound = bindTextureToMaterials(materials, texture);
            synchronized (FORCED_TEXTURES) { FORCED_TEXTURES.put(view, texture); }
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            bitmap.recycle();
            return new BindResult(bound > 0,
                    "png=" + w + "x" + h + " bytes=" + image.bytes.length +
                            " · bufferView=" + image.bufferView + " · boundMaterials=" + bound +
                            " · sampler=baseColorMap");
        } catch (Throwable e) {
            Celine3DDiagnostics.error(context, "V39-158", "Explizite BaseColor-Bindung FEHLER", e);
            return new BindResult(false, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static int bindTextureToMaterials(MaterialInstance[] materials, Texture texture) {
        int bound = 0;
        TextureSampler sampler = new TextureSampler(TextureSampler.MinFilter.LINEAR,
                TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT);
        for (MaterialInstance mi : materials) {
            if (mi == null) continue;
            Material material = mi.getMaterial();
            String samplerName = findBaseColorSampler(material);
            if (samplerName == null) continue;
            mi.setParameter(samplerName, texture, sampler);
            if (material.hasParameter("baseColorIndex")) {
                try { mi.setParameter("baseColorIndex", 0); } catch (Throwable ignored) {}
            }
            set4(mi, "baseColorFactor", 1f, 1f, 1f, 1f);
            applyCleanPbr(mi);
            bound++;
        }
        return bound;
    }

    private static String findBaseColorSampler(Material material) {
        if (material == null) return null;
        if (material.hasParameter("baseColorMap")) return "baseColorMap";
        for (Material.Parameter p : material.getParameters()) {
            if (p.type != Material.Parameter.Type.SAMPLER_2D) continue;
            String n = p.name == null ? "" : p.name.toLowerCase(Locale.ROOT);
            if (n.contains("base") && n.contains("color")) return p.name;
        }
        return null;
    }

    private static void set1(MaterialInstance mi, String name, float value) {
        try { if (mi.getMaterial().hasParameter(name)) mi.setParameter(name, value); } catch (Throwable ignored) {}
    }
    private static void set3(MaterialInstance mi, String name, float x, float y, float z) {
        try { if (mi.getMaterial().hasParameter(name)) mi.setParameter(name, x, y, z); } catch (Throwable ignored) {}
    }
    private static void set4(MaterialInstance mi, String name, float x, float y, float z, float w) {
        try { if (mi.getMaterial().hasParameter(name)) mi.setParameter(name, x, y, z, w); } catch (Throwable ignored) {}
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

    private static ImageBytes extractEmbeddedImage(byte[] bytes) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 28 || bb.getInt(0) != GLB_MAGIC || bb.getInt(4) != 2) {
            throw new IllegalArgumentException("GLB-Header ungültig");
        }
        int declared = bb.getInt(8);
        int pos = 12;
        JSONObject root = null;
        int binOffset = -1, binLength = -1;
        while (pos + 8 <= declared && pos + 8 <= bytes.length) {
            int len = bb.getInt(pos);
            int type = bb.getInt(pos + 4);
            int data = pos + 8;
            if (len < 0 || data + (long) len > bytes.length) break;
            if (type == JSON_CHUNK) {
                String text = new String(bytes, data, len, StandardCharsets.UTF_8).trim();
                root = new JSONObject(text);
            } else if (type == BIN_CHUNK) {
                binOffset = data;
                binLength = len;
            }
            pos = data + len;
        }
        if (root == null || binOffset < 0) throw new IllegalArgumentException("JSON/BIN-Chunk fehlt");
        JSONArray images = root.optJSONArray("images");
        JSONArray views = root.optJSONArray("bufferViews");
        if (images == null || images.length() == 0 || views == null) throw new IllegalArgumentException("Embedded image fehlt");
        JSONObject image = images.optJSONObject(0);
        int bv = image == null ? -1 : image.optInt("bufferView", -1);
        if (bv < 0 || bv >= views.length()) throw new IllegalArgumentException("image.bufferView fehlt");
        JSONObject view = views.optJSONObject(bv);
        int offset = view == null ? 0 : view.optInt("byteOffset", 0);
        int length = view == null ? 0 : view.optInt("byteLength", 0);
        if (offset < 0 || length <= 0 || offset + (long) length > binLength) throw new IllegalArgumentException("PNG bufferView ungültig");
        byte[] imageBytes = new byte[length];
        System.arraycopy(bytes, binOffset + offset, imageBytes, 0, length);
        return new ImageBytes(imageBytes, bv);
    }

    static String analyzeGlb(File glb) {
        if (glb == null || !glb.isFile()) return "glb fehlt";
        try {
            byte[] bytes = readAll(glb);
            ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.length < 20 || bb.getInt(0) != GLB_MAGIC) return "header ungültig";
            int jsonLength = bb.getInt(12);
            if (bb.getInt(16) != JSON_CHUNK || 20L + jsonLength > bytes.length) return "json chunk ungültig";
            JSONObject root = new JSONObject(new String(bytes, 20, jsonLength, StandardCharsets.UTF_8).trim());
            JSONArray materials = root.optJSONArray("materials");
            JSONArray textures = root.optJSONArray("textures");
            JSONArray images = root.optJSONArray("images");
            int baseIndex = -1, baseSource = -1, imageView = -1;
            double metallic = Double.NaN, roughness = Double.NaN;
            boolean emissiveTexture = false;
            if (materials != null && materials.length() > 0) {
                JSONObject m = materials.optJSONObject(0);
                JSONObject pbr = m == null ? null : m.optJSONObject("pbrMetallicRoughness");
                JSONObject bt = pbr == null ? null : pbr.optJSONObject("baseColorTexture");
                baseIndex = bt == null ? -1 : bt.optInt("index", -1);
                metallic = pbr == null ? Double.NaN : pbr.optDouble("metallicFactor", 1.0);
                roughness = pbr == null ? Double.NaN : pbr.optDouble("roughnessFactor", 1.0);
                emissiveTexture = m != null && m.has("emissiveTexture");
            }
            if (textures != null && baseIndex >= 0 && baseIndex < textures.length()) {
                JSONObject t = textures.optJSONObject(baseIndex);
                baseSource = t == null ? -1 : t.optInt("source", -1);
            }
            if (images != null && baseSource >= 0 && baseSource < images.length()) {
                JSONObject image = images.optJSONObject(baseSource);
                imageView = image == null ? -1 : image.optInt("bufferView", -1);
            }
            return "baseTex=" + baseIndex + "→source=" + baseSource + "→bufferView=" + imageView +
                    " · metallic=" + metallic + " roughness=" + roughness + " emissiveTex=" + emissiveTexture;
        } catch (Throwable e) {
            return "analyse=" + e.getClass().getSimpleName();
        }
    }

    private static String joinLimited(List<String> values) {
        StringBuilder b = new StringBuilder();
        for (String v : values) {
            if (b.length() > 0) b.append(" | ");
            b.append(v);
            if (b.length() > 900) { b.append("…"); break; }
        }
        return b.toString();
    }

    private static void copyFile(File from, File to) throws Exception {
        byte[] data = readAll(from);
        writeAtomically(to, data);
    }

    private static void writeAtomically(File target, byte[] data) throws Exception {
        File parent = target.getParentFile();
        if (parent == null) throw new IllegalStateException("Kein Modellordner");
        parent.mkdirs();
        File temp = new File(parent, target.getName() + ".v39.tmp");
        if (temp.exists()) temp.delete();
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
        if (target.exists() && !target.delete()) throw new IllegalStateException("Arbeitsdatei kann nicht ersetzt werden");
        if (!temp.renameTo(target)) throw new IllegalStateException("Arbeitsdatei rename fehlgeschlagen");
    }

    private static byte[] readSelectedModelBytes(Context context) throws Exception {
        File original = originalModelFile(context);
        if (original.isFile() && original.length() > 32L) return readAll(original);
        File imported = Celine3DView.importedModelFile(context);
        if (imported.isFile() && imported.length() > 32L) return readAll(imported);
        try (InputStream in = context.getAssets().open("models/celine.glb")) {
            return readAll(in);
        }
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
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
        StringBuilder b = new StringBuilder();
        for (byte x : digest.digest()) b.append(String.format(Locale.US, "%02x", x & 0xff));
        return b.toString();
    }

    private static String shortSha(String sha) {
        return sha == null ? "?" : sha.substring(0, Math.min(12, sha.length()));
    }

    private static final class BindResult {
        final boolean ok; final String detail;
        BindResult(boolean ok, String detail) { this.ok = ok; this.detail = detail; }
    }

    private static final class ImageBytes {
        final byte[] bytes; final int bufferView;
        ImageBytes(byte[] bytes, int bufferView) { this.bytes = bytes; this.bufferView = bufferView; }
    }
}
