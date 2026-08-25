#!/usr/bin/env bash
set -euo pipefail
SRC='app/src/main/java/de/yahya/ai/WakeWordService.java'
[[ -f "$SRC" ]] || { echo "missing $SRC"; exit 1; }
grep -q 'audioManager.isMusicActive()' "$SRC"
grep -q 'mediaSuspended=true' "$SRC"
grep -q 'recognizer.cancel()' "$SRC"
grep -q 'handler.removeCallbacks(restartRunnable)' "$SRC"
grep -q 'handler.removeCallbacks(mediaGuard)' "$SRC"
if grep -q 'removeCallbacksAndMessages(null)' "$SRC"; then
  echo 'WakeWordService must not wipe the media guard while scheduling recognition restarts.'
  exit 1
fi
# Restart path must be gated while another app is playing media.
grep -q 'destroyed||mediaSuspended' "$SRC"
echo 'PASS: wake-word recognizer yields microphone/audio resources during external media playback.'
