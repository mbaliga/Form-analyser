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

if ! git -C "$shared_dir" diff --quiet HEAD -- performance-analysis local-session-core search-core gradle/libs.versions.toml; then
  echo "shared JVM module sources or version catalog have local changes" >&2
  exit 2
fi
untracked_sources="$(git -C "$shared_dir" status --porcelain --untracked-files=all -- performance-analysis local-session-core search-core gradle/libs.versions.toml)"
if [[ -n "$untracked_sources" ]]; then
  echo "shared JVM module inputs include untracked files:" >&2
  echo "$untracked_sources" >&2
  exit 2
fi

shared_jvm_dir="$(mktemp -d "${TMPDIR:-/tmp}/crocodyl-beta-shared-jvm.XXXXXX")"
trap 'rm -rf "$shared_jvm_dir"' EXIT
mkdir -p "$shared_jvm_dir/gradle"
for module in performance-analysis local-session-core search-core; do
  cp -R "$shared_dir/$module" "$shared_jvm_dir/"
done
cp "$shared_dir/gradle/libs.versions.toml" "$shared_jvm_dir/gradle/"
cp "$repo_root/tools/performance/shared-jvm-settings.gradle.kts" "$shared_jvm_dir/settings.gradle.kts"
cp "$repo_root/tools/performance/shared-jvm-build.gradle.kts" "$shared_jvm_dir/build.gradle.kts"

cd "$repo_root"
./gradlew \
  -PwithSharedPerformance \
  -PsharedLibrariesDir="$shared_jvm_dir" \
  ":performance-report:run" \
  --args="$(printf '%q %q' "$output_json" "$shared_revision")" \
  --offline \
  --no-daemon
