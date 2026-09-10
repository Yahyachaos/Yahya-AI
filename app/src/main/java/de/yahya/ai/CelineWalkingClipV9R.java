package de.yahya.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small runtime sampler derived from the canonical Meshy companion animation GLB.
 *
 * Only local bone rotations plus the clip's vertical hips bob are retained. Horizontal/root
 * translation and scale are deliberately excluded: 9R world-root travel is owned by the bounded
 * room navigator in CelineProductionPresenceV80, so the source clip can never teleport Celine or
 * fight the fixed room/camera contract.
 */
final class CelineWalkingClipV9R {
    static final String ASSET_PATH = "models/celine-walking-v9r.json";
    static final String SOURCE_SHA256 =
            "95c68ce04d85bbffbb2fd3253dc211bb4047283744b0efa83717353b62d03b83";

    private static final String[] REQUIRED_BONES = {
            "Hips", "Spine", "Spine01", "Spine02",
            "LeftShoulder", "RightShoulder", "LeftArm", "RightArm",
            "LeftForeArm", "RightForeArm", "LeftHand", "RightHand",
            "LeftUpLeg", "RightUpLeg", "LeftLeg", "RightLeg", "LeftFoot", "RightFoot"
    };

    private final float durationSeconds;
    private final float[] times;
    private final float[] hipsBobMeters;
    private final Map<String, float[]> rotations;

    private CelineWalkingClipV9R(float durationSeconds, float[] times, float[] hipsBobMeters,
                                 Map<String, float[]> rotations) {
        this.durationSeconds = durationSeconds;
        this.times = times;
        this.hipsBobMeters = hipsBobMeters;
        this.rotations = Collections.unmodifiableMap(rotations);
    }

    static CelineWalkingClipV9R load(Context context) throws Exception {
        JSONObject root = readJson(context, ASSET_PATH);
        require(root.optInt("schema") == 1, "schema");
        JSONObject source = root.getJSONObject("source");
        require(SOURCE_SHA256.equals(source.optString("sha256")), "source sha256");
        require("Walking".equals(root.optString("animation")), "animation name");

        float duration = finitePositive(root, "duration_s");
        require(Math.abs(duration - 1.0333333f) <= 0.0002f, "duration");

        JSONArray timeJson = root.getJSONArray("sample_times_s");
        JSONArray bobJson = root.getJSONArray("hips_bob_m");
        require(timeJson.length() >= 3 && bobJson.length() == timeJson.length(), "sample counts");
        float[] times = new float[timeJson.length()];
        float[] bob = new float[bobJson.length()];
        float previous = -1.0f;
        for (int i = 0; i < times.length; i++) {
            times[i] = finite(timeJson, i, "time");
            bob[i] = finite(bobJson, i, "bob");
            require(times[i] > previous, "strictly increasing times");
            require(Math.abs(bob[i]) <= 0.08f, "bounded hips bob");
            previous = times[i];
        }
        require(Math.abs(times[0]) <= 0.0001f, "first sample zero");
        require(Math.abs(times[times.length - 1] - duration) <= 0.0002f, "last sample duration");

        JSONObject channelJson = root.getJSONObject("rotations_delta_xyzw");
        Map<String, float[]> channels = new LinkedHashMap<>();
        for (String name : REQUIRED_BONES) {
            JSONArray values = channelJson.optJSONArray(name);
            require(values != null && values.length() == times.length, "rotation channel " + name);
            float[] flat = new float[times.length * 4];
            for (int i = 0; i < values.length(); i++) {
                JSONArray q = values.getJSONArray(i);
                require(q.length() == 4, "quat width " + name);
                float x = finite(q, 0, name + " qx");
                float y = finite(q, 1, name + " qy");
                float z = finite(q, 2, name + " qz");
                float w = finite(q, 3, name + " qw");
                float norm = (float) Math.sqrt(x*x + y*y + z*z + w*w);
                require(norm > 0.98f && norm < 1.02f, "quat norm " + name);
                int o = i * 4;
                flat[o] = x / norm;
                flat[o + 1] = y / norm;
                flat[o + 2] = z / norm;
                flat[o + 3] = w / norm;
            }
            channels.put(name, flat);
        }

        Celine3DDiagnostics.record(context, "V80-470", "9R Walking-Clip geladen",
                "sourceSha256=" + SOURCE_SHA256 + " duration=" + duration
                        + " samples=" + times.length + " bones=" + channels.size()
                        + " rootTranslation=navOnly headNeck=block6");
        return new CelineWalkingClipV9R(duration, times, bob, channels);
    }

    float durationSeconds() {
        return durationSeconds;
    }

    boolean sampleRotation(String boneName, double clipSeconds, float blend, float[] out) {
        float[] channel = rotations.get(boneName);
        if (channel == null || out == null || out.length < 4) return false;
        int[] span = span(clipSeconds);
        int a = span[0], b = span[1];
        float alpha = Float.intBitsToFloat(span[2]);
        int oa = a * 4, ob = b * 4;
        slerp(channel, oa, channel, ob, alpha, out);
        float boundedBlend = clamp(blend, 0.0f, 1.0f);
        if (boundedBlend < 0.9999f) {
            float[] identity = {0.0f, 0.0f, 0.0f, 1.0f};
            float[] sampled = {out[0], out[1], out[2], out[3]};
            slerp(identity, 0, sampled, 0, boundedBlend, out);
        }
        return true;
    }

    float sampleHipsBob(double clipSeconds, float blend) {
        int[] span = span(clipSeconds);
        int a = span[0], b = span[1];
        float alpha = Float.intBitsToFloat(span[2]);
        return (hipsBobMeters[a] + (hipsBobMeters[b] - hipsBobMeters[a]) * alpha)
                * clamp(blend, 0.0f, 1.0f);
    }

    private int[] span(double rawSeconds) {
        double wrapped = rawSeconds % durationSeconds;
        if (wrapped < 0.0) wrapped += durationSeconds;
        float value = (float) wrapped;
        int upper = 1;
        while (upper < times.length && times[upper] <= value) upper++;
        if (upper >= times.length) upper = times.length - 1;
        int lower = Math.max(0, upper - 1);
        float range = times[upper] - times[lower];
        float alpha = range <= 0.000001f ? 0.0f : (value - times[lower]) / range;
        return new int[]{lower, upper, Float.floatToIntBits(clamp(alpha, 0.0f, 1.0f))};
    }

    private static void slerp(float[] a, int oa, float[] b, int ob, float t, float[] out) {
        float ax=a[oa], ay=a[oa+1], az=a[oa+2], aw=a[oa+3];
        float bx=b[ob], by=b[ob+1], bz=b[ob+2], bw=b[ob+3];
        float dot=ax*bx+ay*by+az*bz+aw*bw;
        if (dot < 0.0f) {
            bx=-bx; by=-by; bz=-bz; bw=-bw; dot=-dot;
        }
        dot=clamp(dot, -1.0f, 1.0f);
        float wa, wb;
        if (dot > 0.9995f) {
            wa=1.0f-t; wb=t;
        } else {
            double theta=Math.acos(dot);
            double sin=Math.sin(theta);
            wa=(float)(Math.sin((1.0-t)*theta)/sin);
            wb=(float)(Math.sin(t*theta)/sin);
        }
        float x=wa*ax+wb*bx, y=wa*ay+wb*by, z=wa*az+wb*bz, w=wa*aw+wb*bw;
        float norm=(float)Math.sqrt(x*x+y*y+z*z+w*w);
        if (!(norm > 0.000001f)) {
            out[0]=out[1]=out[2]=0.0f; out[3]=1.0f;
            return;
        }
        out[0]=x/norm; out[1]=y/norm; out[2]=z/norm; out[3]=w/norm;
    }

    static void quaternionMatrix(float[] q, float[] out) {
        float x=q[0], y=q[1], z=q[2], w=q[3];
        float xx=x*x, yy=y*y, zz=z*z;
        float xy=x*y, xz=x*z, yz=y*z;
        float xw=x*w, yw=y*w, zw=z*w;
        out[0]=1f-2f*(yy+zz); out[1]=2f*(xy+zw); out[2]=2f*(xz-yw); out[3]=0f;
        out[4]=2f*(xy-zw); out[5]=1f-2f*(xx+zz); out[6]=2f*(yz+xw); out[7]=0f;
        out[8]=2f*(xz+yw); out[9]=2f*(yz-xw); out[10]=1f-2f*(xx+yy); out[11]=0f;
        out[12]=0f; out[13]=0f; out[14]=0f; out[15]=1f;
    }

    private static JSONObject readJson(Context context, String path) throws Exception {
        StringBuilder text = new StringBuilder(32_768);
        try (InputStream in = context.getAssets().open(path);
             InputStreamReader reader = new InputStreamReader(in, "UTF-8")) {
            char[] chunk = new char[8_192];
            int read;
            while ((read = reader.read(chunk)) >= 0) {
                if (read > 0) text.append(chunk, 0, read);
            }
        }
        return new JSONObject(text.toString());
    }

    private static float finitePositive(JSONObject object, String key) throws Exception {
        float value=(float)object.getDouble(key);
        require(!Float.isNaN(value) && !Float.isInfinite(value) && value > 0.0f, key);
        return value;
    }

    private static float finite(JSONArray array, int index, String label) throws Exception {
        double value=array.getDouble(index);
        require(!Double.isNaN(value) && !Double.isInfinite(value), label);
        return (float)value;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("9R walking clip invalid: " + label);
    }
}