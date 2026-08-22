# Yahya AI · Celin v1.0 Foundation

Diese Version ist der saubere v1.0-Neustart auf Basis des funktionierenden v0.9-Projekts.

## Direkt verbessert
- Deutsch (`de-DE`) ist fest als Standard für Spracherkennung und KI-Antworten gesetzt.
- Fremdsprachen werden nur noch verwendet, wenn sie ausdrücklich angefordert werden.
- Celin steht größer wie in einer Videochat-Ansicht auf der Hauptseite.
- Der vorhandene Celin-Avatar bleibt unverändert; keine neue Figur wurde erzeugt.
- Kontinuierliche Mikrobewegungen für ruhigeres, lebendigeres Verhalten: Atmung, leichtes Schwanken, Zustände für Zuhören/Denken/Sprechen und Touch-Reaktion.
- Stimmeinstellungen wurden verständlicher benannt.
- Emojis/Markdown werden weiterhin vor der Sprachausgabe bereinigt.
- AndroidIDE-Setup bleibt reproduzierbar über `bash SETUP_ANDROIDIDE.sh`.

## Offline-Neuralstimme
Eine echte in die App eingebettete neuronale Stimme braucht ein lokales TTS-Modell plus Android-Laufzeit. Diese Foundation behauptet deshalb nicht fälschlich, dass Android-TTS bereits eine eigene Celin-Stimme wäre. Als Ziel ist eine lokale ONNX/Sherpa-ONNX-Stimme vorgesehen. Bis das Modell tatsächlich mitgeliefert und auf dem Zielgerät getestet ist, bleibt die lokale Fallback-Stimme die installierte Android-TTS-Engine.

## Nächster technischer Schritt
- lokale TTS-Runtime integrieren
- deutsches lizenziertes Modell als Celin Voice Pack mitliefern
- Audio-Streaming an die Avatarbewegung koppeln
- echte visuelle Lippenzustände/Face-Rig ergänzen, ohne Celins freigegebenes Gesicht neu zu gestalten
