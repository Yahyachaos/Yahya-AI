# Yahya AI v69

- Celins sichtbare A-Pose wird in HOME und CALL durch eine eng begrenzte Vier-Gelenk-Pose gelöst.
- Nur LeftArm, RightArm, LeftForeArm und RightForeArm werden bewegt; Schultern, Hüfte, Root, Beine, Gesicht und Kamera bleiben unverändert.
- HOME senkt beide Arme körpernah ab, CALL hält die Arme entspannt und winkelt die Unterarme leicht an.
- Beim Wechsel HOME → CALL → HOME wird die jeweils passende Arm-Pose sauber übernommen.
- Bei einem Laufzeitfehler werden die exakten Ausgangstransformen automatisch wiederhergestellt und der neue Arm-Regler deaktiviert.
- Der echte Produktionsavatar muss HOME, CALL und HOME-Rückkehr auf dem exakten Build sichtbar bestehen; Deformationen blockieren den Merge.
- Die in v68 geprüfte Tastatur-Fokussierung und exakte Avatar-/Eingabe-/Videochat-Geometrie bleiben unverändert.
- Die glaubwürdige sitzende CALL-Ganzkörperpose bleibt der nächste sichere Teil desselben A-Pose-/Grundhaltungs-Schritts.
