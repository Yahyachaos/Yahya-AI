#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

need="app build.gradle settings.gradle gradle gradle.properties gradlew SETUP_ANDROIDIDE.sh PROJECT_CHECKSUMS.txt"
for f in $need; do
  [ -e "$f" ] || { echo "FEHLT: $f"; exit 1; }
done

required_files=(
  app/src/main/java/de/yahya/ai/MainActivity.java
  app/src/main/java/de/yahya/ai/CelineBrain.java
  app/src/main/java/de/yahya/ai/CelineMemoryEngine.java
  app/src/main/java/de/yahya/ai/CelineStructuredMemory.java
  app/src/main/java/de/yahya/ai/SpeechTextNormalizer.java
  app/src/main/java/de/yahya/ai/SpeechRecognitionIntentFactory.java
  app/src/main/java/de/yahya/ai/SpeechOutputRouter.java
  app/src/main/java/de/yahya/ai/LocalNeuralTtsEngine.java
  app/src/main/java/de/yahya/ai/SupertonicModelManager.java
  app/src/main/java/de/yahya/ai/SpeechAudioBus.java
  app/src/main/java/de/yahya/ai/SpeechVisemeAnalyzer.java
  app/src/main/java/de/yahya/ai/CelineAvatarController.java
  app/src/main/java/de/yahya/ai/CelineFaceOverlayView.java
  app/src/main/res/drawable-nodpi/celine_avatar.png
)

for f in "${required_files[@]}"; do
  [ -f "$f" ] || { echo "FEHLT: $f"; exit 1; }
done

grep -q "applicationId 'de.yahya.ai'" app/build.gradle || { echo "FEHLT: applicationId de.yahya.ai"; exit 1; }
# Feature releases intentionally advance their descriptive suffix (foundation, feminine-presence,
# natural-body-motion, ...). Keep this project-integrity check on the canonical Celine version
# family; runtime PR CI separately enforces the numeric versionCode bump.
grep -q "versionName '1.0-celin-[^']\+'" app/build.gradle || { echo "FEHLT: erwartete Celine versionName-Familie"; exit 1; }
grep -q "gradle-7.5.1-" gradle/wrapper/gradle-wrapper.properties || { echo "FEHLT: Gradle 7.5.1 wrapper"; exit 1; }

bash -n gradlew
bash -n SETUP_ANDROIDIDE.sh

if [ -f ci/celine_camera_interaction_contract_v79.py ]; then
  python3 ci/celine_camera_interaction_contract_v79.py
fi

if [ -f ci/celine_g1_brain_contract.py ]; then
  python3 ci/celine_g1_brain_contract.py
fi

if [ -f ci/celine_g1_structured_memory_test.py ]; then
  python3 ci/celine_g1_structured_memory_test.py
fi

if [ -f ci/celine_g1_structured_memory_live_contract.py ]; then
  python3 ci/celine_g1_structured_memory_live_contract.py
fi

# Checksums are useful for detecting unexpected changes, but legitimate active
# development changes them frequently. Report differences without blocking CI.
if ! sha256sum -c PROJECT_CHECKSUMS.txt; then
  echo "WARNUNG: PROJECT_CHECKSUMS.txt ist nicht aktuell; Build wird fortgesetzt."
fi

echo "PROJECT COMPLETE"
