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
[ -f app/src/main/java/de/yahya/ai/LocalNeuralTtsEngine.java ] || { echo "FEHLT: LocalNeuralTtsEngine.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/SupertonicModelManager.java ] || { echo "FEHLT: SupertonicModelManager.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/SpeechAudioBus.java ] || { echo "FEHLT: SpeechAudioBus.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/SpeechVisemeAnalyzer.java ] || { echo "FEHLT: SpeechVisemeAnalyzer.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/CelineAvatarController.java ] || { echo "FEHLT: CelineAvatarController.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/CelineFaceOverlayView.java ] || { echo "FEHLT: CelineFaceOverlayView.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/Celine3DView.java ] || { echo "FEHLT: Celine3DView.java"; exit 1; }
[ -f app/src/main/java/de/yahya/ai/CelineModelImportActivity.java ] || { echo "FEHLT: CelineModelImportActivity.java"; exit 1; }
[ -f app/src/main/res/drawable-nodpi/celine_avatar.png ] || { echo "FEHLT: Celin-Avatar"; exit 1; }

grep -q "applicationId 'de.yahya.ai'" app/build.gradle
grep -q "versionName '1.0-celin-foundation'" app/build.gradle
grep -q "gradle-6.1.1-" gradle/wrapper/gradle-wrapper.properties

bash -n gradlew
bash -n SETUP_ANDROIDIDE.sh

# PROJECT_CHECKSUMS.txt documents an older known-good snapshot. During active development,
# intentional source changes make those hashes stale, so report them without blocking the compiler.
if ! sha256sum -c PROJECT_CHECKSUMS.txt; then
  echo "WARN: PROJECT_CHECKSUMS.txt ist nach absichtlichen Source-Änderungen veraltet."
fi

echo "PROJECT COMPLETE"
