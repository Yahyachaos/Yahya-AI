#!/bin/bash
set -e
cd "$(dirname "$0")"

need="app build.gradle settings.gradle gradle gradle.properties gradlew SETUP_ANDROIDIDE.sh PROJECT_CHECKSUMS.txt"
for f in $need; do
  [ -e "$f" ] || { echo "FEHLT: $f"; exit 1; }
done

[ -f app/src/main/java/de/yahya/ai/MainActivity.java ] || { echo "FEHLT: MainActivity.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/SpeechTextNormalizer.java ] || { echo "FEHLT: SpeechTextNormalizer.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/SpeechRecognitionIntentFactory.java ] || { echo "FEHLT: SpeechRecognitionIntentFactory.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/SpeechOutputRouter.java ] || { echo "FEHLT: SpeechOutputRouter.java"; exit 1; }
[ -f app/src/main/res/drawable-nodpi/celine_avatar.png ] || { echo "FEHLT: Celin-Avatar"; exit 1; }

grep -q "applicationId 'de.yahya.ai'" app/build.gradle
grep -q "versionName '1.0-celin-foundation'" app/build.gradle
grep -q "gradle-6.1.1-" gradle/wrapper/gradle-wrapper.properties

bash -n gradlew
bash -n SETUP_ANDROIDIDE.sh
sha256sum -c PROJECT_CHECKSUMS.txt

echo "PROJECT COMPLETE"
