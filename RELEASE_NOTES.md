### Stand v50

- Die bewährte **v49-Sichtbarkeitsbasis bleibt unangetastet**: TRUE-UNLIT/FORCE-C sowie die riskanten v46/v48 Skin-Matrix-Layer werden nicht verändert bzw. nicht wieder aktiviert.
- Auf der **Startseite** wird Celines 3D-Bühne kompakter, damit der Gesprächsverlauf und der Schreibbereich deutlich mehr Platz bekommen.
- Der Schreibbereich erhält eine feste Zugänglichkeits-/Prüfmarke und Android nutzt `adjustResize`, damit das Eingabefeld auch mit geöffneter Tastatur im sichtbaren Bereich bleibt.
- Im **Live-Videochat** darf dieselbe 3D-Bühne den verfügbaren Call-Bereich vollständig ausfüllen, statt die kompakte HOME-Höhe mitzunehmen.
- Die neue CI-Prüfung kontrolliert nicht nur Avatar-Pixel auf HOME und CALL, sondern zusätzlich die sichtbare Schreibfläche auf HOME und die vergrößerte Bühne im Live-Videochat.
- Der In-App-Updater bleibt ausschließlich in **Einstellungen → App & Updates**.

Weitere natürliche Körper-, Gesichts- und Gesprächsbewegungen werden weiterhin nur in kleinen Schritten aktiviert und müssen vor einer Veröffentlichung den HOME- und CALL-Sichtbarkeitstest bestehen.
