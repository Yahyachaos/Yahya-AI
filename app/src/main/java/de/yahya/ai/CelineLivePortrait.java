package de.yahya.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Loads the approved Celin live-call portrait bundled as compact base64 WebP. */
public final class CelineLivePortrait {
    private static Bitmap cached;

    private CelineLivePortrait() {}

    public static synchronized Bitmap load(Context context) {
        if (cached != null && !cached.isRecycled()) return cached;
        if (context == null) return null;
        InputStream in = null;
        try {
            in = context.getAssets().open("celine_live_neutral.b64");
            ByteArrayOutputStream out = new ByteArrayOutputStream(12000);
            byte[] buffer = new byte[2048];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            byte[] encoded = out.toByteArray();
            byte[] image = Base64.decode(encoded, Base64.DEFAULT);
            cached = BitmapFactory.decodeByteArray(image, 0, image.length);
            return cached;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }
}
