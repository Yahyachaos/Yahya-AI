#!/bin/bash
set -e
cd "$(dirname "$0")"
echo "Yahya AI v1.0 - AndroidIDE Setup"
echo "Projekt: $(pwd)"

for f in app/build.gradle build.gradle settings.gradle gradle/wrapper/gradle-wrapper.properties; do
  if [ ! -e "$f" ]; then
    echo "FEHLER: Projektdatei fehlt: $f"
    exit 1
  fi
done

# AndroidIDE Play ruft exakt ./gradlew auf. Deshalb wird die Datei hier immer sichergestellt.
if [ ! -f gradlew ]; then
  cat > gradlew <<'INNER'
#!/bin/bash
set -e
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "FEHLER: Gradle nicht gefunden. Fuehre SETUP_ANDROIDIDE.sh in AndroidIDE aus." >&2
exit 1
INNER
fi
chmod +x gradlew

if ! command -v gradle >/dev/null 2>&1; then
  echo "FEHLER: AndroidIDE-Gradle ist nicht verfuegbar."
  exit 1
fi

echo "[1/4] Gradle Wrapper erzeugen/vervollstaendigen..."
gradle wrapper --gradle-version 6.1.1
sed -i '1c#!/bin/bash' gradlew
chmod +x gradlew

echo "[2/4] Wrapper pruefen..."
test -f gradlew
test -f gradle/wrapper/gradle-wrapper.properties
test -f gradle/wrapper/gradle-wrapper.jar

echo "[3/4] Debug-Build testen..."
./gradlew assembleDebug --console=plain

echo "[4/4] Fertig."
echo "BUILD READY - jetzt in AndroidIDE auf Play druecken."
