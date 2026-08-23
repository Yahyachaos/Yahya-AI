package de.yahya.ai;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** In-app picker for importing Celine's 3D avatar without relying on Android's Open-with chooser. */
public final class AvatarPickerActivity extends Activity {
    private static final int REQ_PICK_AVATAR = 7001;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null) openPicker();
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "model/gltf-binary",
                "application/octet-stream",
                "application/zip",
                "application/x-zip-compressed"
        });
        try {
            startActivityForResult(i, REQ_PICK_AVATAR);
        } catch (Throwable e) {
            Toast.makeText(this, "Dateiauswahl konnte nicht geöffnet werden.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_AVATAR) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }

        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Throwable ignored) {}

        Intent importer = new Intent(this, CelineModelImportActivity.class);
        importer.setData(uri);
        importer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(importer);
        finish();
    }
}
