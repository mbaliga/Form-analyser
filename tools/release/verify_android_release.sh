#!/usr/bin/env bash
set -eo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 RELEASE_APK RELEASE_AAB" >&2
  exit 2
fi

APK="$1"
AAB="$2"
POSE_SHA256='59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a'

test -s "$APK"
test -s "$AAB"
test -n "$ANDROID_HOME"
test -n "$EXPECTED_VERSION_CODE"
test -n "$EXPECTED_VERSION_NAME"

APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
AAPT2="$(find "$ANDROID_HOME/build-tools" -type f -name aapt2 -print | sort -V | tail -n 1)"
ZIPALIGN="$(find "$ANDROID_HOME/build-tools" -type f -name zipalign -print | sort -V | tail -n 1)"
test -x "$APKSIGNER"
test -x "$AAPT2"
test -x "$ZIPALIGN"

"$APKSIGNER" verify --verbose "$APK"
"$ZIPALIGN" -c -P 16 4 "$APK"
jarsigner -verify "$AAB"

MANIFEST="$(mktemp /tmp/crocodyl-release-manifest.XXXXXX)"
trap 'rm -f "$MANIFEST"' EXIT
"$AAPT2" dump xmltree "$APK" --file AndroidManifest.xml > "$MANIFEST"
BADGING="$("$AAPT2" dump badging "$APK")"
printf '%s\n' "$BADGING" | grep -F "versionCode='$EXPECTED_VERSION_CODE' versionName='$EXPECTED_VERSION_NAME'"
grep -Eq 'usesCleartextTraffic.*=false' "$MANIFEST"
grep -Eq 'allowBackup.*=false' "$MANIFEST"
grep -A8 -B3 'androidx.core.content.FileProvider' "$MANIFEST" | grep -Eq 'exported.*=false'

unzip -l "$APK" | grep -q 'assets/pose_landmarker_lite.task'
unzip -l "$AAB" | grep -q 'base/assets/pose_landmarker_lite.task'
test "$(unzip -p "$APK" assets/pose_landmarker_lite.task | sha256sum | cut -d' ' -f1)" = "$POSE_SHA256"
test "$(unzip -p "$AAB" base/assets/pose_landmarker_lite.task | sha256sum | cut -d' ' -f1)" = "$POSE_SHA256"
unzip -l "$AAB" | grep -q 'base/res/xml/backup_rules.xml'
unzip -l "$AAB" | grep -q 'base/res/xml/data_extraction_rules.xml'

if unzip -l "$APK" | grep -Eq '\.partial([[:space:]]|$)' || unzip -l "$AAB" | grep -Eq '\.partial([[:space:]]|$)'; then
  echo 'Release bundle contains a staging file.' >&2
  exit 1
fi

echo "Android release preflight passed: $APK and $AAB"
