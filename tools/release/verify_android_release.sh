#!/usr/bin/env bash
set -eo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 RELEASE_APK RELEASE_AAB" >&2
  exit 2
fi

APK="$1"
AAB="$2"
POSE_SHA256='59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a'

sha256_stream() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{print $1}'
  else
    echo 'Neither sha256sum nor shasum is available.' >&2
    return 1
  fi
}

test -s "$APK"
test -s "$AAB"
test -n "$ANDROID_HOME"
test -n "$EXPECTED_VERSION_CODE"
test -n "$EXPECTED_VERSION_NAME"
test -n "$EXPECTED_CERT_SHA256"

EXPECTED_CERT_SHA256="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
printf '%s' "$EXPECTED_CERT_SHA256" | grep -Eq '^[0-9a-f]{64}$'

APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
AAPT2="$(find "$ANDROID_HOME/build-tools" -type f -name aapt2 -print | sort -V | tail -n 1)"
ZIPALIGN="$(find "$ANDROID_HOME/build-tools" -type f -name zipalign -print | sort -V | tail -n 1)"
test -x "$APKSIGNER"
test -x "$AAPT2"
test -x "$ZIPALIGN"

"$APKSIGNER" verify --verbose "$APK"
"$ZIPALIGN" -c -P 16 4 "$APK"
jarsigner -verify "$AAB"

APK_CERT_SHA256="$("$APKSIGNER" verify --print-certs "$APK" | awk -F': ' '/certificate SHA-256 digest:/{print tolower($2); exit}')"
AAB_CERT_SHA256="$(keytool -printcert -jarfile "$AAB" | awk -F': ' '/SHA256:/{gsub(":", "", $2); print tolower($2); exit}')"
test "$APK_CERT_SHA256" = "$EXPECTED_CERT_SHA256"
test "$AAB_CERT_SHA256" = "$EXPECTED_CERT_SHA256"

MANIFEST="$(mktemp /tmp/crocodyl-release-manifest.XXXXXX)"
trap 'rm -f "$MANIFEST"' EXIT
"$AAPT2" dump xmltree "$APK" --file AndroidManifest.xml > "$MANIFEST"
BADGING="$("$AAPT2" dump badging "$APK")"
python3 tools/release/verify_manifest_contract.py "$MANIFEST"
printf '%s\n' "$BADGING" | grep -F "package: name='xyz.mdhv.formanalyser'"
printf '%s\n' "$BADGING" | grep -F "versionCode='$EXPECTED_VERSION_CODE' versionName='$EXPECTED_VERSION_NAME'"
grep -Eq 'usesCleartextTraffic.*=false' "$MANIFEST"
grep -Eq 'allowBackup.*=false' "$MANIFEST"
grep -A8 -B3 'androidx.core.content.FileProvider' "$MANIFEST" | grep -Eq 'exported.*=false'

unzip -l "$APK" | grep 'assets/pose_landmarker_lite.task' >/dev/null
unzip -l "$AAB" | grep 'base/assets/pose_landmarker_lite.task' >/dev/null
test "$(unzip -p "$APK" assets/pose_landmarker_lite.task | sha256_stream)" = "$POSE_SHA256"
test "$(unzip -p "$AAB" base/assets/pose_landmarker_lite.task | sha256_stream)" = "$POSE_SHA256"
unzip -l "$AAB" | grep 'base/res/xml/backup_rules.xml' >/dev/null
unzip -l "$AAB" | grep 'base/res/xml/data_extraction_rules.xml' >/dev/null

if unzip -l "$APK" | grep -E '\.partial([[:space:]]|$)' >/dev/null || unzip -l "$AAB" | grep -E '\.partial([[:space:]]|$)' >/dev/null; then
  echo 'Release bundle contains a staging file.' >&2
  exit 1
fi

echo "Android release preflight passed: $APK and $AAB"
