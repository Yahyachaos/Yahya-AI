#!/bin/bash
set -e
# Yahya AI AndroidIDE launcher. AndroidIDE expects exactly ./gradlew.
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "FEHLER: Gradle wurde in AndroidIDE nicht gefunden. Fuehre zuerst aus: bash SETUP_ANDROIDIDE.sh" >&2
exit 1
