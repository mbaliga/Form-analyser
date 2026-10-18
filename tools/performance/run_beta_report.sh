#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 /absolute/path/to/Shared-Libraries-asoc [output-json] [shared-revision]" >&2
  exit 2
fi

shared_dir="$1"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
output_json="${2:-performance-report/build/reports/beta/performance-report.json}"
if [[ "$output_json" != /* ]]; then
  output_json="$repo_root/$output_json"
fi

if [[ ! -d "$shared_dir" ]]; then
  echo "Shared-Libraries-asoc checkout not found: $shared_dir" >&2
  exit 2
fi

python3 "$repo_root/tools/shared/verify_shared_checkout.py" "$shared_dir"
actual_revision="$(git -C "$shared_dir" rev-parse HEAD)"
shared_revision="${3:-$actual_revision}"
if [[ "$shared_revision" != "$actual_revision" ]]; then
  echo "requested report revision $shared_revision does not match checkout $actual_revision" >&2
  exit 2
fi

cd "$repo_root"
./gradlew \
  -PwithSharedPerformance \
  -PsharedLibrariesDir="$shared_dir" \
  ":performance-report:run" \
  --args="$(printf '%q %q' "$output_json" "$shared_revision")" \
  --offline \
  --no-daemon
