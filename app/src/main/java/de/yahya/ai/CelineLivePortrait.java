package de.yahya.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Loads the approved Celin live-call portraits bundled as compact base64 WebP assets. */
public final class CelineLivePortrait {
    public enum Pose { NEUTRAL, LISTENING, SMILE, SPEAKING }

    private static final Map<Pose, Bitmap> CACHE = new HashMap<>();

    private CelineLivePortrait() {}

    public static synchronized Bitmap load(Context context) {
        return load(context, Pose.NEUTRAL);
    }

    public static synchronized Bitmap load(Context context, Pose pose) {
        if (context == null) return null;
        if (pose == null) pose = Pose.NEUTRAL;

        Bitmap cached = CACHE.get(pose);
        if (cached != null && !cached.isRecycled()) return cached;

        String asset;
        switch (pose) {
            case LISTENING: asset = "celine_live_listening.b64"; break;
            case SMILE: asset = "celine_live_smile.b64"; break;
            case SPEAKING: asset = "celine_live_speaking.b64"; break;
            case NEUTRAL:
            default: asset = "celine_live_neutral.b64"; break;
        }

        Bitmap bitmap = decodeAsset(context, asset);
        if (bitmap == null && pose != Pose.NEUTRAL) bitmap = decodeAsset(context, "celine_live_neutral.b64");
        if (bitmap != null) CACHE.put(pose, bitmap);
        return bitmap;
    }

    private static Bitmap decodeAsset(Context context, String asset) {
        InputStream in = null;
        try {
            in = context.getAssets().open(asset);
            ByteArrayOutputStream out = new ByteArrayOutputStream(20000);
            byte[] buffer = new byte[2048];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            byte[] image = Base64.decode(out.toByteArray(), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(image, 0, image.length);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }
}
