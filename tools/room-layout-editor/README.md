# Celine Raumeditor – isolierter Browser-Prototyp

**Nur im Experiment-Branch** `experiment/visual-room-editor-v83`. Keine Android-App-, PR-#111-, Release-, Original-GLB-, Core- oder Buildänderung. Es gibt **keine automatische Veröffentlichung**.

## Start am PC

1. Das Repository mit genau diesem Branch auschecken:
   ```bash
   git clone -b experiment/visual-room-editor-v83 https://github.com/Yahyachaos/Yahya-AI.git
   cd Yahya-AI
   git lfs pull
   ```
   Falls bereits ein vollständiger Clone vorhanden ist: zuerst lokale Änderungen sichern und den Experiment-Branch in einem **separaten Worktree/Ordner** auschecken.
2. Optionaler statischer Selbsttest (Node.js): `node tools/room-layout-editor/smoke-check.cjs`.
3. Im **Repository-Stammordner** einen lokalen HTTP-Server starten: `python -m http.server 8765` (unter Windows gegebenenfalls `py -m http.server 8765`).
4. Im Browser öffnen: **http://localhost:8765/tools/room-layout-editor/**. Three.js wird beim ersten Öffnen von einem CDN geladen; Internet wird benötigt. `file://` funktioniert wegen ES-Modulen, Modell-URLs und Fetch nicht zuverlässig.

## Bedienung

- Die ermittelten festen **Innenmaße** sind **4,40 m breit × 4,20 m tief × 2,65 m hoch**, Ursprung Fußbodenmitte, X links/rechts, Y Höhe, Z Tiefe; nur nach explizitem Aktivieren kann der Raum verändert werden.
- Das Repository enthält zwölf source-fidelity-GLBs mit **dreizehn unabhängig bearbeitbaren Instanzen** (zwei Nachttische). Anklicken oder im Dropdown auswählen.
- **Verschieben**, **Drehen**, **Skalieren** mit 3D-Gizmos; die drei numerischen Positionen und Winkel lassen sich unabhängig ändern. Buttons für fein dosierte X/Y/Z-Schritte und Wenden um 180° je Achse. Freie Ansicht, Draufsicht und vereinfachte CALL-Seite.
- **Möbel verankern** sperrt genau diese Instanz gegen Maus-, Zahlen-, Schritt- und Rücksetz-Bearbeitung; „Nächstes freies Möbel“ springt zum nächsten unverankerten Objekt. Der Sperrstatus überlebt Speichern/Laden. Das bewusste **Entsperren** ist jederzeit möglich.
- **Originalreferenz `Refernzbild.png`** wird aus dem geklonten Repository geladen. Bild-Overlay, Deckkraft und eigenes Referenzbild können angepasst werden.
- Browser speichert Änderungen lokal; mit **Layout speichern (.json)** entsteht eine portable Sicherung aller 13 IDs, XYZ-Positionen in Metern, X/Y/Z-Drehwinkel in Grad, XYZ-Skalierung, Sperrzustände und Raummaßen.
- **Assembly exportieren** erstellt lediglich einen ***EDITOR_CANDIDATE.json***, keine geänderte GLB und keine aktualisierte APK. Die erweiterten X/Z-Winkel und Einzelachsenskalierung benötigen vor einer echten Android-Übernahme einen gesonderten, validierten Filament-Importer.

## Grenzen und Sicherheit

Die alten Positionswerte in `celine_room_v80_assembly.json` sind ausdrücklich **ein verworfener Ausgangsentwurf** mit **6,40 × 5,80 × 2,80 m**; sie werden **nicht** als neue Raumgröße oder visuell korrekte Anordnung übernommen. Deshalb können Objekte am Anfang außerhalb der Innenmaße liegen. Der Editor meldet solche Ankerpunkte; die Prüfung ist kein vollständiger Kollisions- oder Objektgrenzennachweis.

Die 3D-Vorschau ist eine **Three.js-Entwicklungsansicht** und weder ein pixelidentischer Android-Filament-Render noch eine kalibrierte Produktionskamera. GLBs bleiben unverändert. Fehlende GLBs werden als gelbe Platzhalter gezeigt und als Ladefehler gemeldet. Browser-Canvas/Renderer und echte visuelle Übereinstimmung müssen noch **manuell im Browser** überprüft werden; ein rein statischer Smoke-Test beweist das nicht.

Zum späteren Übertragen: Kandidatenlayout gegen den tatsächlich verwendeten Filament-Transformationspfad (Root, Pivot, Importer, Kamera, Wandkollision, Navigation, Celine-Interaktionen) prüfen, einmalig bewusst übernehmen und Android-Test durchführen. Bis dahin wird `main`, Room-PR #111 und der offizielle Updateknopf nicht verändert.

## Entkoppelte Prüfungen (Browser-Code vs. Bilder)

GitHub Actions verwendet zwei unabhängige Jobs: `functional_browser` prüft die 13 echten GLBs, WebGL, die numerische Möbelbedienung, einzelne Verankerungen und den JSON-Speichern-/Laden-/Neustart-Ablauf **ohne Screenshots**. Erst nach dessen Erfolg wird `optional_visual` auf einem eigenen Runner ausgeführt. Dieser zweite Job prüft direktes Anklicken und Ziehen im WebGL-Canvas sowie echte PNG-Raster. Sein `continue-on-error` verhindert, dass ein Bild-Timeout den bereits bestandenen Pflicht-Funktionstest entwertet; sein tatsächlicher PASS/FAIL und die Bildartefakte bleiben separat sichtbar. Die Beweisdateien heißen `celine-room-editor-functional-evidence` und `celine-room-editor-optional-visual-evidence`. Die Bilder sind **kein visueller Gesamt-PASS** für das Android-Raumreferenzziel. Es wurde kein neuer zeitgesteuerter Worker angelegt.
