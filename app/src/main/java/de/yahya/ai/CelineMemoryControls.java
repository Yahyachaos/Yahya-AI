package de.yahya.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

/** User-owned G1.5 inspect/correct/forget controls for structured memory. */
final class CelineMemoryControls {
    private CelineMemoryControls() {}

    static void show(Activity activity, CelineStructuredMemory memory) {
        if (activity == null || memory == null) return;
        memory.consolidateNow();
        final List<CelineMemoryItem> items = memory.inspectItems();
        final String protection = memory.protectedStorageAvailable()
                ? "Geschützter Speicher: aktiv"
                : "Geschützter Speicher: momentan nicht verfügbar; neue Erinnerungen werden nicht unverschlüsselt gespeichert.";
        if (items.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Celins Gedächtnis")
                    .setMessage(protection + "\n\nNoch keine dauerhaften Erinnerungen.")
                    .setPositiveButton("Schließen", null)
                    .show();
            return;
        }

        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            CelineMemoryItem item = items.get(i);
            labels[i] = item.type.name() + " · " + shortText(item.summary, 86);
        }
        new AlertDialog.Builder(activity)
                .setTitle("Celins Gedächtnis")
                .setMessage(protection + "\n\nTippe eine Erinnerung an, um sie zu prüfen, zu korrigieren oder einzeln zu löschen.")
                .setItems(labels, (dialog, which) -> showItem(activity, memory, items.get(which)))
                .setNegativeButton("Schließen", null)
                .show();
    }

    private static void showItem(Activity activity, CelineStructuredMemory memory, CelineMemoryItem item) {
        if (item == null) return;
        String detail = "Typ: " + item.type.name()
                + "\nStatus: " + item.knowledgeState.name()
                + "\nDatenschutz: " + item.privacyScope.name()
                + "\nQuelle: " + (item.provenance.isEmpty() ? "unbekannt" : item.provenance)
                + "\n\n" + item.summary;
        new AlertDialog.Builder(activity)
                .setTitle("Erinnerung")
                .setMessage(detail)
                .setPositiveButton("Korrigieren", (dialog, which) -> showCorrection(activity, memory, item))
                .setNeutralButton("Löschen", (dialog, which) -> confirmForget(activity, memory, item))
                .setNegativeButton("Zurück", (dialog, which) -> show(activity, memory))
                .show();
    }

    private static void showCorrection(Activity activity, CelineStructuredMemory memory, CelineMemoryItem item) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMaxLines(5);
        input.setText(item.summary);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(activity)
                .setTitle("Erinnerung korrigieren")
                .setMessage("Die Korrektur ersetzt genau diese Erinnerung und wird als explizite Nutzerkorrektur gespeichert.")
                .setView(input)
                .setPositiveButton("Speichern", (dialog, which) -> {
                    String replacement = input.getText().toString().trim();
                    boolean saved = memory.correct(item.id, replacement);
                    Toast.makeText(activity,
                            saved ? "Erinnerung korrigiert." : "Korrektur nicht gespeichert.",
                            Toast.LENGTH_SHORT).show();
                    show(activity, memory);
                })
                .setNegativeButton("Abbrechen", (dialog, which) -> showItem(activity, memory, item))
                .show();
    }

    private static void confirmForget(Activity activity, CelineStructuredMemory memory, CelineMemoryItem item) {
        new AlertDialog.Builder(activity)
                .setTitle("Diese Erinnerung löschen?")
                .setMessage(item.summary)
                .setPositiveButton("Löschen", (dialog, which) -> {
                    memory.forget(item.id);
                    Toast.makeText(activity, "Erinnerung gelöscht.", Toast.LENGTH_SHORT).show();
                    show(activity, memory);
                })
                .setNegativeButton("Abbrechen", (dialog, which) -> showItem(activity, memory, item))
                .show();
    }

    private static String shortText(String text, int max) {
        String clean = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }
}
