#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN is required}"
: "${TARGET_SHA:?TARGET_SHA is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

CHECKED_SHA="$(git rev-parse HEAD)"
if [ "$CHECKED_SHA" != "$TARGET_SHA" ]; then
  echo "Release checkout does not match tested target."
  echo "target=$TARGET_SHA checked=$CHECKED_SHA"
  exit 1
fi

VERSION_CODE="$(grep -E '^[[:space:]]*versionCode[[:space:]]+[0-9]+' app/build.gradle | grep -oE '[0-9]+' | head -1)"
VERSION_NAME="$(grep -E "^[[:space:]]*versionName[[:space:]]+'[^']+'" app/build.gradle | sed -E "s/.*versionName[[:space:]]+'([^']+)'.*/\1/" | head -1)"
if [ -z "$VERSION_CODE" ]; then
  echo "Could not determine versionCode"
  exit 1
fi

TAG="v${VERSION_CODE}"
TITLE="Yahya AI v${VERSION_CODE}"
APK_NAME="Yahya-AI-v${VERSION_CODE}.apk"
SOURCE_APK="release-apk/app-release.apk"
if [ ! -f "$SOURCE_APK" ]; then
  echo "Tested APK missing: $SOURCE_APK"
  exit 1
fi
# Never publish the separate debug Actions artifact under a production filename.
case "$SOURCE_APK" in */app-release.apk) ;; *) echo 'Refusing non-release artifact'; exit 1 ;; esac
cp "$SOURCE_APK" "$APK_NAME"
LOCAL_DIGEST="sha256:$(sha256sum "$APK_NAME" | awk '{print $1}')"

# Enforce exact Android package/version and unchanged installed-release signature.
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AAPT="${SDK_DIR}/build-tools/30.0.3/aapt"
APKSIGNER="${SDK_DIR}/build-tools/30.0.3/apksigner"
[ -x "$AAPT" ] && [ -x "$APKSIGNER" ] || { echo "Required Android signing/package tools missing"; exit 1; }
BADGING="$("$AAPT" dump badging "$APK_NAME" | grep -m1 '^package:')"
if [[ "$BADGING" != *"name='de.yahya.ai'"* || "$BADGING" != *"versionCode='$VERSION_CODE'"* ]]; then
  echo "Release package/version mismatch: $BADGING"; exit 1;
fi
if "$AAPT" dump badging "$APK_NAME" | grep -q '^application-debuggable'; then
  echo 'FATAL: debuggable APK cannot be published as stable release'; exit 1
fi
"$APKSIGNER" verify "$APK_NAME" >/dev/null
LIVE_MAIN_SHA="$(gh api "repos/${GITHUB_REPOSITORY}/commits/main" --jq '.sha')"
if [ "$TARGET_SHA" != "$LIVE_MAIN_SHA" ]; then
  echo "Refusing release unless exact validated target is current main"; exit 1
fi
SIGN_CHECK_DIR="$(mktemp -d)"
gh release download v80 --repo "$GITHUB_REPOSITORY" --pattern 'Yahya-AI-v80.apk' --dir "$SIGN_CHECK_DIR" >/dev/null
OLD_APK="$SIGN_CHECK_DIR/Yahya-AI-v80.apk"
[ -s "$OLD_APK" ] || { echo "Existing official v80 APK missing for signer comparison"; exit 1; }
cert_digest() {
  "$APKSIGNER" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1
}
OLD_CERT="$(cert_digest "$OLD_APK")"
NEW_CERT="$(cert_digest "$APK_NAME")"
if [ -z "$OLD_CERT" ] || [ -z "$NEW_CERT" ] || [ "$OLD_CERT" != "$NEW_CERT" ]; then
  echo "Release APK signature is not compatible with existing official v80/v82"; exit 1;
fi
echo "Package, versionCode, and installed-update signing parity: PASS" >> "$GITHUB_STEP_SUMMARY"

NOTES_FILE="$(mktemp)"
trap 'rm -rf "$SIGN_CHECK_DIR"; rm -f "$NOTES_FILE" release-readback.json' EXIT
cat > "$NOTES_FILE" <<EOF
# Yahya AI v${VERSION_CODE}

- Exact validated main commit: \`${TARGET_SHA}\`.
- Android versionCode: \`${VERSION_CODE}\`.
- Android versionName: \`${VERSION_NAME:-unknown}\`.
- APK asset: \`${APK_NAME}\`.
- This release is published only after the exact-main Android/emulator gates succeed.
EOF

# Owner-approved v83-specific known visual defects, never a whole-Room visual PASS.
if [ "$VERSION_CODE" = "83" ]; then
cat >> "$NOTES_FILE" <<'V83_VISUAL_FAIL_NOTES'

## Known visual issues — explicitly accepted by the owner for this v83 interim release only

This is an official **functional interim release**, not a claim of a reference-perfect Room. Real Android HOME/CALL comparison remains **VISUAL FAIL**: dark gray/strongly striped curtains, incorrect bright window bars/openings, room architecture/furnishings/materials/lighting/perspective gaps, and visibly non-reference Celine. The GLASS/open-window/backplane owner decision is still unresolved; **G2.3 PAUSED**. Room visual perfection continues after v83. Security, memory, updater, build and signing gates are NOT waived.

## Critical signing risk — explicitly accepted for this interim version

**The historical Android production signing private key was publicly exposed in the project's public source repository.** This v83 APK intentionally uses the same certificate as previously installed v80/v82 to preserve in-place upgrade compatibility. Matching the certificate does **not** establish a secure or exclusive publisher identity: anyone who obtained the exposed private key could produce an apparently correctly signed replacement. The owner explicitly authorized this known risk for the v83 functional interim release; this does not heal the compromise, and safe key-custody/rotation and Android 8 migration remain unresolved future security work. Obtain the APK only from the project's verified GitHub Release and check its published SHA-256 digest. The digest detects changes relative to that release, but does not restore an uncompromised signing identity.
V83_VISUAL_FAIL_NOTES
fi

# Guard against a stale copied release body before GitHub is touched.
if ! grep -Fqx "# Yahya AI v${VERSION_CODE}" "$NOTES_FILE"; then
  echo "Generated release notes do not identify the current version."
  exit 1
fi
if [ "$VERSION_CODE" = "83" ] && ! grep -Fq 'historical Android production signing private key was publicly exposed' "$NOTES_FILE"; then
  echo "Refusing v83 release without the required compromised-signer risk disclosure."
  exit 1
fi

git fetch --tags --force
if git rev-parse -q --verify "refs/tags/$TAG^{commit}" >/dev/null; then
  EXISTING_SHA="$(git rev-list -n 1 "$TAG")"
  if [ "$EXISTING_SHA" != "$TARGET_SHA" ]; then
    echo "Refusing to overwrite existing release tag $TAG."
    echo "existing=$EXISTING_SHA target=$TARGET_SHA"
    exit 1
  fi
  if ! gh release view "$TAG" >/dev/null 2>&1; then
    gh release create "$TAG" "$APK_NAME" \
      --verify-tag \
      --title "$TITLE" \
      --notes-file "$NOTES_FILE" \
      --latest
  fi
else
  gh release create "$TAG" "$APK_NAME" \
    --title "$TITLE" \
    --notes-file "$NOTES_FILE" \
    --target "$TARGET_SHA" \
    --latest
fi

# Re-fetch the tag created by gh and bind it to the exact validated main SHA.
git fetch --tags --force
PUBLISHED_TAG_SHA="$(git rev-list -n 1 "$TAG")"
if [ "$PUBLISHED_TAG_SHA" != "$TARGET_SHA" ]; then
  echo "Published tag does not resolve to exact validated main."
  echo "tag=$PUBLISHED_TAG_SHA target=$TARGET_SHA"
  exit 1
fi

gh api "repos/${GITHUB_REPOSITORY}/releases/tags/${TAG}" > release-readback.json
python3 - "$TAG" "$TITLE" "$TARGET_SHA" "$APK_NAME" "$VERSION_CODE" "$LOCAL_DIGEST" <<'PY'
import json
import sys

path = "release-readback.json"
tag, title, target_sha, apk_name, version, local_digest = sys.argv[1:]
data = json.load(open(path, "r", encoding="utf-8"))
errors = []
if data.get("tag_name") != tag:
    errors.append(f"tag_name={data.get('tag_name')!r} expected={tag!r}")
if data.get("name") != title:
    errors.append(f"name={data.get('name')!r} expected={title!r}")
body = data.get("body") or ""
required_heading = f"# Yahya AI v{version}"
if required_heading not in body:
    errors.append("release body does not contain current-version heading")
if target_sha not in body:
    errors.append("release body does not contain exact validated main SHA")
assets = [asset for asset in data.get("assets", []) if asset.get("name") == apk_name]
if len(assets) != 1:
    errors.append(f"expected exactly one asset named {apk_name}, found {len(assets)}")
else:
    remote_digest = assets[0].get("digest")
    if remote_digest and remote_digest != local_digest:
        errors.append(f"asset digest={remote_digest!r} expected={local_digest!r}")
    if not remote_digest:
        errors.append("GitHub release asset did not expose a digest for read-back verification")
if errors:
    raise SystemExit("Release read-back validation failed:\n- " + "\n- ".join(errors))
print(json.dumps({
    "status": "PASS",
    "tag": tag,
    "title": title,
    "target_sha": target_sha,
    "asset": apk_name,
    "digest": local_digest,
}, indent=2))
PY

echo "Published and read-back verified $TAG for exact tested main $TARGET_SHA ($LOCAL_DIGEST)" >> "$GITHUB_STEP_SUMMARY"
