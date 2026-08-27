#!/usr/bin/env bash
set -euo pipefail

REF="${1:-HEAD}"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

git rev-parse --verify "${REF}^{commit}" >/dev/null

runtime_path() {
  case "$1" in
    app/*|build.gradle|settings.gradle|gradle.properties|gradle/*|gradlew|gradlew.bat|VERIFY_PROJECT.sh|.gitattributes|signing/*|ci/CELINE_SOURCE_ASSET.json|docs/celine/reference/v2/*|ci/celine_production_morph_v65.py|ci/celine_facial_morph_validation.py|ci/celine_character_refresh_v75.py|ci/celine_character_refresh_validation_v75.py|ci/celine_material_regions_v75.py|ci/celine_material_regions_validation_v75.py|ci/celine_facial_rig_v76.py|ci/celine_facial_rig_validation_v76.py|ci/celine_blink_localization_v79.py|ci/celine_blink_localization_validation_v79.py)
      return 0 ;;
    *) return 1 ;;
  esac
}

while IFS=$'\t' read -r meta path; do
  runtime_path "$path" || continue
  printf '%s\t%s\n' "$meta" "$path" >> "$TMP"
done < <(git ls-tree -r "$REF")

if [ ! -s "$TMP" ]; then
  echo "No runtime-relevant inputs found at ref $REF" >&2
  exit 1
fi

LC_ALL=C sort -o "$TMP" "$TMP"
if [ "${CELINE_RUNTIME_FINGERPRINT_VERBOSE:-0}" = "1" ]; then
  cat "$TMP" >&2
fi
sha256sum "$TMP" | awk '{print $1}'
