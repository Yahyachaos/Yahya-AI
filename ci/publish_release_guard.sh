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
SOURCE_APK="release-apk/app-debug.apk"
if [ ! -f "$SOURCE_APK" ]; then
  echo "Tested APK missing: $SOURCE_APK"
  exit 1
fi
cp "$SOURCE_APK" "$APK_NAME"
LOCAL_DIGEST="sha256:$(sha256sum "$APK_NAME" | awk '{print $1}')"

NOTES_FILE="$(mktemp)"
trap 'rm -f "$NOTES_FILE" release-readback.json' EXIT
cat > "$NOTES_FILE" <<EOF
# Yahya AI v${VERSION_CODE}

- Exact validated main commit: \`${TARGET_SHA}\`.
- Android versionCode: \`${VERSION_CODE}\`.
- Android versionName: \`${VERSION_NAME:-unknown}\`.
- APK asset: \`${APK_NAME}\`.
- This release is published only after the exact-main Android/emulator gates succeed.
EOF

# Guard against a stale copied release body before GitHub is touched.
if ! grep -Fqx "# Yahya AI v${VERSION_CODE}" "$NOTES_FILE"; then
  echo "Generated release notes do not identify the current version."
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
