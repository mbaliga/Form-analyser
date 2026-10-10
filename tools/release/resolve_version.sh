#!/usr/bin/env bash
set -euo pipefail

raw_version="${INPUT_VERSION:-${REF_NAME:-}}"
version="${raw_version#release/}"

# This value becomes an artifact filename and Gradle property. Keep it shell-safe even though
# callers pass it through environment variables; release inputs must never become shell source.
if [[ ! "$version" =~ ^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$ ]]; then
  echo "Release version must be 1–64 shell-safe ASCII characters." >&2
  exit 1
fi

if [[ "$version" =~ ^v?([0-9]+)\.([0-9]+)\.([0-9]+)([-+][A-Za-z0-9._+-]*)?$ ]]; then
  version_code="$(python3 -c 'import sys; major, minor, patch = map(int, sys.argv[1:]); code = major * 10000 + minor * 100 + patch; print(code) if 1 <= code <= 2100000000 else sys.exit(1)' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}")" || {
    echo "Derived versionCode is outside Android's supported range." >&2
    exit 1
  }
elif [[ "${INPUT_VERSION_CODE:-}" =~ ^[1-9][0-9]*$ ]]; then
  version_code="$INPUT_VERSION_CODE"
else
  echo "A semver release name or a positive versionCode input is required." >&2
  exit 1
fi

if ! python3 -c 'import sys; n = int(sys.argv[1]); sys.exit(0 if 1 <= n <= 2100000000 else 1)' "$version_code"; then
  echo "Derived versionCode is outside Android's supported range: $version_code" >&2
  exit 1
fi

printf 'version=%s\nversion_code=%s\n' "$version" "$version_code" >> "$GITHUB_OUTPUT"
