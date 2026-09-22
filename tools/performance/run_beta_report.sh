#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 /absolute/path/to/Shared-Libraries-asoc [output-json] [shared-revision]" >&2
  exit 2
fi

shared_dir="$1"
output_json="${2:-build/reports/beta/performance-report.json}"
shared_revision="${3:-$(git -C "$shared_dir" rev-parse HEAD)}"

if [[ ! -d "$shared_dir" ]]; then
  echo "Shared-Libraries-asoc checkout not found: $shared_dir" >&2
  exit 2
fi

./gradlew \
  -PwithSharedPerformance \
  -PsharedLibrariesDir="$shared_dir" \
  ":performance-report:run" \
  --args="$(printf '%q %q' "$output_json" "$shared_revision")" \
  --offline \
  --no-daemon
