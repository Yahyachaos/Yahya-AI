#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/ci/CELINE_SOURCE_ASSET.json"
SOURCE="$ROOT/app/src/main/assets/models/celine-source/Meshy_AI_biped_Character_output.glb"
EXPECTED_SHA="0c9fa09f898fbc8c0503be252c8fec1ee815a3a4990422e5c302e3113d7c1b55"
EXPECTED_SIZE="27381856"

if [ ! -f "$MANIFEST" ]; then
  echo "ERROR: missing Celine source manifest: $MANIFEST" >&2
  exit 2
fi
if [ ! -f "$SOURCE" ]; then
  echo "ERROR: canonical Celine source GLB missing: $SOURCE" >&2
  echo "Run git lfs pull or upload the canonical source through Git LFS." >&2
  exit 3
fi

ACTUAL_SIZE="$(wc -c < "$SOURCE" | tr -d ' ')"
ACTUAL_SHA="$(sha256sum "$SOURCE" | awk '{print $1}')"

if [ "$ACTUAL_SIZE" != "$EXPECTED_SIZE" ]; then
  echo "ERROR: Celine source size mismatch: expected=$EXPECTED_SIZE actual=$ACTUAL_SIZE" >&2
  exit 4
fi
if [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  echo "ERROR: Celine source SHA256 mismatch: expected=$EXPECTED_SHA actual=$ACTUAL_SHA" >&2
  exit 5
fi

if head -n 1 "$SOURCE" | grep -q '^version https://git-lfs.github.com/spec/v1$'; then
  echo "ERROR: only the Git LFS pointer is present; run git lfs pull." >&2
  exit 6
fi

echo "PASS: canonical Celine source present and verified"
echo "path=$SOURCE"
echo "sha256=$ACTUAL_SHA"
echo "size=$ACTUAL_SIZE"
