#!/usr/bin/env bash
set -euo pipefail

raw_version="${INPUT_VERSION:-${REF_NAME:-}}"
# `tag` is the release name exactly as given (a release/<name> branch contributes <name>); it is the
# Git tag the release publishes. `version` starts equal to it and loses one leading "v" below, once
# it is known to be a semver name, so the Android versionName and the asset names carry no "v".
tag="${raw_version#release/}"
version="$tag"
semver_regex='^v?([0-9]+)\.([0-9]+)\.([0-9]+)([-+][A-Za-z0-9._+-]*)?$'
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
last_issued_version_code="$(tr -d '[:space:]' < "$script_dir/highest_issued_version_code.txt")"

if [[ ! "$last_issued_version_code" =~ ^(0|[1-9][0-9]*)$ ]] || \
  ! python3 -c 'import sys; n = int(sys.argv[1]); sys.exit(0 if 0 <= n <= 2100000000 else 1)' "$last_issued_version_code"; then
  echo "The checked-in highest-issued versionCode is invalid." >&2
  exit 1
fi

# This value becomes an artifact filename and Gradle property. Keep it shell-safe even though
# callers pass it through environment variables; release inputs must never become shell source.
if [[ ! "$version" =~ ^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$ ]]; then
  echo "Release version must be 1–64 shell-safe ASCII characters." >&2
  exit 1
fi

if [[ -n "${INPUT_VERSION_CODE:-}" ]]; then
  if [[ ! "$INPUT_VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
    echo "versionCode must be a positive integer." >&2
    exit 1
  fi
  version_code="$INPUT_VERSION_CODE"
elif [[ "$version" =~ $semver_regex ]]; then
  if [[ -n "${BASH_REMATCH[4]:-}" ]]; then
    echo "Semver prerelease/build labels require an explicit versionCode." >&2
    exit 1
  fi
  version_code="$(python3 -c 'import sys; major, minor, patch = map(int, sys.argv[1:]); code = major * 10000 + minor * 100 + patch; print(code) if 0 <= major <= 210000 and 0 <= minor <= 99 and 0 <= patch <= 99 and 1 <= code <= 2100000000 else sys.exit(1)' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}")" || {
    echo "Semver components cannot map uniquely into Android's versionCode range (minor and patch must be below 100)." >&2
    exit 1
  }
else
  echo "A plain semver release name or a positive versionCode input is required." >&2
  exit 1
fi

if ! python3 -c 'import sys; n = int(sys.argv[1]); sys.exit(0 if 1 <= n <= 2100000000 else 1)' "$version_code"; then
  echo "Derived versionCode is outside Android's supported range: $version_code" >&2
  exit 1
fi

if (( version_code <= last_issued_version_code )); then
  echo "versionCode $version_code must be greater than the last issued versionCode $last_issued_version_code. Supply a higher explicit versionCode or use a version whose derived code is higher." >&2
  exit 1
fi

if [[ "$version" =~ $semver_regex ]]; then
  version="${version#v}"
fi

# The version the build stamps into the APK comes from the committed app-android/version.properties
# (F-Droid builds the tag with none of CI's -P overrides), so a release is only valid when its
# commit already carries the same name and code.
properties_file="${VERSION_PROPERTIES_FILE:-$script_dir/../../app-android/version.properties}"
if [[ ! -f "$properties_file" ]]; then
  echo "app-android/version.properties is missing ($properties_file); a release commit must carry its version there." >&2
  exit 1
fi

read_property() {
  awk -v key="$1" '
    { sub(/\r$/, "") }
    /^[ \t]*#/ { next }
    {
      i = index($0, "=")
      if (i == 0) next
      k = substr($0, 1, i - 1); v = substr($0, i + 1)
      gsub(/^[ \t]+|[ \t]+$/, "", k); gsub(/^[ \t]+|[ \t]+$/, "", v)
      if (k == key) value = v
    }
    END { print value }
  ' "$properties_file"
}

properties_version_name="$(read_property versionName)"
properties_version_code="$(read_property versionCode)"
if [[ -z "$properties_version_name" || -z "$properties_version_code" ]]; then
  echo "app-android/version.properties must define both versionName and versionCode." >&2
  exit 1
fi
if [[ "$version" != "$properties_version_name" || "$version_code" != "$properties_version_code" ]]; then
  echo "release $version/$version_code does not match app-android/version.properties; bump it in the release commit" >&2
  echo "(version.properties has versionName=$properties_version_name versionCode=$properties_version_code)" >&2
  exit 1
fi

printf 'tag=%s\nversion=%s\nversion_code=%s\n' "$tag" "$version" "$version_code" >> "$GITHUB_OUTPUT"
