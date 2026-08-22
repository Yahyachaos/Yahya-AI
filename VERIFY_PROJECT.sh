#!/bin/bash
set -e
cd "$(dirname "$0")"
need="app build.gradle settings.gradle gradle gradle.properties gradlew SETUP_ANDROIDIDE.sh"
for f in $need; do
  [ -e "$f" ] || { echo "FEHLT: $f"; exit 1; }
done
[ -f app/src/main/java/de/yahya/ai/MainActivity.java ] || { echo "FEHLT: MainActivity.java"; exit 1; }
[ -f app/src/main/res/drawable-nodpi/celine_avatar.png ] || { echo "FEHLT: Celin-Avatar"; exit 1; }
bash -n gradlew
bash -n SETUP_ANDROIDIDE.sh
echo "PROJECT COMPLETE"
