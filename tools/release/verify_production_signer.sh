#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 EXPECTED_PRODUCTION_CERT_SHA256" >&2
  exit 2
fi

NORMALIZED_CERT="$(printf '%s' "$1" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
if ! printf '%s' "$NORMALIZED_CERT" | grep -Eq '^[0-9a-f]{64}$'; then
  echo 'Expected production certificate fingerprint must be 64 hexadecimal characters.' >&2
  exit 2
fi

# The checked-in debug key is intentionally public and shared by QA/CI builds. It must never be
# accepted as the configured production release identity, even if the secret fingerprint matches.
QA_DEBUG_CERT_SHA256='36f33a33f9415fbe4f31556a3dde9bc010d03c502bb3dfe6b2bfaf0cbafdaebc'
if [ "$NORMALIZED_CERT" = "$QA_DEBUG_CERT_SHA256" ]; then
  echo 'The committed QA/debug certificate cannot sign a production release.' >&2
  exit 1
fi
