### Stand v49

- **Celine-Sichtbarkeit hat absolute Priorität:** Die v46/v48 experimentellen Pose-/Skin-Matrix-Layer sind vorerst deaktiviert, weil das echte Samsung-Gerät gezeigt hat, dass der Raum sichtbar bleiben kann, während das 3D-Mesh verschwindet.
- Rückkehr auf die letzte bekannte sichtbare Produktionsbasis: **v43 TRUE-UNLIT/FORCE-C + v44 Raum/Präsentation + v45 Live-Videochat + v47 Call-Lock/Updater**.
- Die funktionierende TRUE-UNLIT / FORCE-C-Texturpipeline wird nicht verändert.
- Der Emulator-Test bekommt jetzt ein echtes, synthetisches Filament-GLB im privaten `files/models/celine.glb`-Pfad. Damit wird nicht mehr nur geprüft, ob die App startet.
- CI verlangt sichtbare 3D-Avatar-Pixel sowohl auf der **Startseite** als auch nach dem Öffnen von **Live mit Celin**. Nur wenn beide Prüfungen bestehen, darf ein Release veröffentlicht werden.
- Screenshots, UI-Dumps, Logcat und App-Diagnosedaten werden bei jedem Emulatorlauf als Beweismaterial gespeichert.
- Der In-App-Button **Update prüfen** bleibt erhalten.

Die natürliche Sitz-/Gesprächsbewegung wird erst wieder schrittweise aktiviert, nachdem jede Änderung den neuen HOME- und CALL-Sichtbarkeitstest bestanden hat.
