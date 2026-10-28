#!/usr/bin/env bash
# Disposable Android signing-lineage compatibility probe. NEVER consume production keys.
# Only produces a synthetic de.yahya.signingprobe package; never signs or publishes Yahya AI.
set -euo pipefail
mode="${1:?prepare|emulator}"
sdk="$ANDROID_HOME"
work="$RUNNER_TEMP/celine-rotation-fixtures"
proof="$GITHUB_WORKSPACE/signing-rotation-evidence"
pkg="de.yahya.signingprobe"
aapt="$sdk/build-tools/35.0.0/aapt"
align="$sdk/build-tools/35.0.0/zipalign"
signer="$sdk/build-tools/35.0.0/apksigner"
mkdir -p "$work" "$proof"

sign_args=(--ks-pass pass:ephemeral-test-only --key-pass pass:ephemeral-test-only)
make_manifest() {
  local version="$1"
  cat > "$work/manifest-$version.xml" <<EOF
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="de.yahya.signingprobe"
    android:versionCode="$version" android:versionName="$version">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/>
    <application android:label="Disposable Signing Probe" android:debuggable="true"/>
</manifest>
EOF
  "$aapt" package -f -M "$work/manifest-$version.xml" \
    -I "$sdk/platforms/android-35/android.jar" -F "$work/unsigned-$version.apk"
  "$align" -f -p 4 "$work/unsigned-$version.apk" "$work/aligned-$version.apk"
}
if [ "$mode" = "prepare" ]; then
  command -v keytool >/dev/null
  for exe in "$aapt" "$align" "$signer"; do test -x "$exe"; done
  for label in old new; do
    keytool -genkeypair -noprompt -storetype JKS -keystore "$work/$label.jks" \
      -alias "$label" -keyalg RSA -keysize 2048 -validity 2 \
      -storepass ephemeral-test-only -keypass ephemeral-test-only \
      -dname "CN=Disposable $label Signing Probe" >/dev/null 2>&1
  done
  make_manifest 80
  make_manifest 83
  "$signer" sign --min-sdk-version 26 --ks "$work/old.jks" \
    --ks-key-alias old "${sign_args[@]}" --out "$work/v80-old.apk" "$work/aligned-80.apk"
  "$signer" rotate --out "$work/lineage.bin" \
    --old-signer --ks "$work/old.jks" --ks-key-alias old "${sign_args[@]}" \
    --new-signer --ks "$work/new.jks" --ks-key-alias new "${sign_args[@]}"
  "$signer" sign --min-sdk-version 26 --ks "$work/new.jks" \
    --ks-key-alias new "${sign_args[@]}" --out "$work/v83-new-only.apk" "$work/aligned-83.apk"
  "$signer" sign --min-sdk-version 26 --ks "$work/old.jks" \
    --ks-key-alias old "${sign_args[@]}" --next-signer \
    --ks "$work/new.jks" --ks-key-alias new "${sign_args[@]}" \
    --lineage "$work/lineage.bin" --out "$work/v83-rotate-default.apk" "$work/aligned-83.apk"
  "$signer" sign --min-sdk-version 26 --ks "$work/old.jks" \
    --ks-key-alias old "${sign_args[@]}" --next-signer \
    --ks "$work/new.jks" --ks-key-alias new "${sign_args[@]}" \
    --lineage "$work/lineage.bin" --rotation-min-sdk-version 28 \
    --out "$work/v83-rotate-28.apk" "$work/aligned-83.apk"
  for api in 26 27 28 32 33; do
    for name in v80-old v83-new-only v83-rotate-default v83-rotate-28; do
      "$signer" verify --min-sdk-version "$api" --max-sdk-version "$api" \
        "$work/$name.apk" >/dev/null
    done
    echo "API $api: all fixture APK cryptographic signatures independently verify" \
      >> "$proof/apksigner-platform-verification.txt"
  done
  old_cert=$("$signer" verify --print-certs "$work/v80-old.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1)
  new_cert=$("$signer" verify --print-certs "$work/v83-new-only.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1)
  test -n "$old_cert" && test -n "$new_cert" && test "$old_cert" != "$new_cert"
  printf 'Old synthetic signer SHA256: %s\nNew synthetic signer SHA256: %s\n' \
    "$old_cert" "$new_cert" > "$proof/synthetic-certificate-identities.txt"
  echo 'PREPARED_DISPOSABLE_OLD_NEW_SIGNER_LINEAGE_AND_V80_V83_FIXTURES_PASS'
  exit 0
fi

if [ "$mode" = "emulator" ]; then
  api="${2:?Android API level}"
  test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "$api"
  log="$proof/api-$api.txt"
  : > "$log"
  echo "Running Android API $api synthetic signer migration" | tee -a "$log"
  check_version() {
    adb shell dumpsys package "$pkg" | grep -m1 -F "versionCode=$1" >/dev/null
  }
  check_data() {
    test "$(adb shell "run-as $pkg cat files/preserved.txt" | tr -d '\r\n')" = 'retained'
  }
  for kind in default forced28; do
    # Each variant starts from newly installed synthetic v80 with independently signed data.
    adb uninstall "$pkg" >/dev/null 2>&1 || true
    adb install "$work/v80-old.apk" >> "$log" 2>&1
    check_version 80
    adb shell "run-as $pkg sh -c 'mkdir -p files; echo retained > files/preserved.txt'"
    check_data
    if adb install -r "$work/v83-new-only.apk" >> "$log" 2>&1; then
      echo 'FAIL: unrelated new key without proof-of-rotation unexpectedly replaced old install' | tee -a "$log"
      exit 1
    fi
    check_version 80
    check_data
    echo "API $api: new-only in-place update rejected, old data retained" | tee -a "$log"
    if [ "$kind" = default ]; then
      candidate="$work/v83-rotate-default.apk"
    else
      candidate="$work/v83-rotate-28.apk"
    fi
    adb install -r "$candidate" >> "$log" 2>&1
    check_version 83
    check_data
    echo "API $api: $kind lineage in-place update succeeds and preserves existing data" | tee -a "$log"
  done
  echo "API_${api}_SYNTHETIC_ROTATION_INSTALL_AND_RETENTION_PASS" | tee -a "$log"
  exit 0
fi
echo "Unknown test mode: $mode" >&2
exit 2
