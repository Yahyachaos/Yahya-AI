# Yahya AI v54

- Celine erhält erstmals wieder einen echten, sichtbaren Skinning-Schritt – absichtlich nur am `Head`-Joint und mit sehr kleinen Bewegungen auf dem echten Avatar.
- Die alte v46-Mehrknochen-/Sitzpose bleibt deaktiviert. Vor jedem Skinning-Update werden Hals und Wirbelsäulen-Knoten auf ihre sichere Basis zurückgesetzt.
- `Animator.updateBoneMatrices()` wird nur durch die neue isolierte v54-Schicht aktiviert; bei einem Laufzeitfehler deaktiviert sie sich und stellt die Basispose wieder her.
- Der bisherige HOME/CALL/HOME-return-Sichtbarkeitstest bleibt vollständig erhalten.
- Zusätzlich läuft ein zweiter CI-Test mit einem minimalen 1-Joint-GLB. Drei Emulator-Screenshots müssen sowohl sichtbare Avatar-Pixel als auch eine messbare Bewegung der skinned Geometrie nachweisen.
- TRUE-UNLIT/FORCE-C, v52/v53 Kamera-Präsenz, HOME-Composer, CALL-Bühne und Updater in Einstellungen bleiben geschützt.
