# v50 Layout Guard

V50 baut bewusst auf der grünen v49-Sichtbarkeitsbasis auf.

- Keine Reaktivierung von v46/v48 Skin-Matrix-/Pose-Layern.
- HOME: kompaktere Celine-Bühne, mehr Platz für Gespräch und Eingabe.
- Android `adjustResize`, damit der Composer bei geöffneter Tastatur sichtbar bleibt.
- CALL: die vorhandene Bühne füllt den v45-Call-Slot statt die HOME-Höhe mitzunehmen.
- CI prüft zusätzlich zu HOME/CALL-Avatar-Pixeln auch Composer-Sichtbarkeit und Call-Stage-Größe.

Die Layout-Schicht verändert weder TRUE-UNLIT/FORCE-C noch Filament-Materialien oder Skin-Matrizen.
