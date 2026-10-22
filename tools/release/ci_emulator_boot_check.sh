#!/usr/bin/env bash
# Observable emulator preflight for CI: install a system image, create an AVD, boot it and wait for
# sys.boot_completed. android-emulator-runner hides its output inside a `uses:` step, so on failure
# this script emits bundled workflow annotations (GitHub keeps 10 per step) with the emulator log.
# Usage: ci_emulator_boot_check.sh <api-level> <image-tag>   e.g. 35 google_apis
set -uo pipefail

api="${1:?api level}"
tag="${2:?image tag}"
name="ci-${api}-${tag}"
image="system-images;android-${api};${tag};x86_64"
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
avdmanager="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
emulator="$ANDROID_HOME/emulator/emulator"
log_dir="${RUNNER_TEMP:-/tmp}/emulator-boot-check"
mkdir -p "$log_dir" "${ANDROID_AVD_HOME:?ANDROID_AVD_HOME must be set}"

annotate() { # $1 = title; stdin = lines
  local body
  body="$(tr '\r' '\n' | grep -v -E '^\[=* *\]|^$' | tail -n 25 | cut -c1-220 | sed 's/%/%25/g' | sed 's/$/%0A/' | tr -d '\n')"
  [ -n "$body" ] && echo "::error title=$1::$body"
  return 0
}
fail() { echo "::error title=emulator preflight $name::$1"; exit 1; }

yes | "$sdkmanager" --licenses > "$log_dir/licenses.log" 2>&1 || true
"$sdkmanager" --install "emulator" "platform-tools" "$image" > "$log_dir/install.log" 2>&1 \
  || { annotate "sdkmanager $image" < "$log_dir/install.log"; fail "sdkmanager could not install $image"; }
echo no | "$avdmanager" create avd -n "$name" -k "$image" -d "${AVD_DEVICE:-pixel_2}" --force > "$log_dir/avd.log" 2>&1 \
  || { annotate "avdmanager $name" < "$log_dir/avd.log"; fail "avdmanager could not create $name"; }

listed="$("$emulator" -list-avds 2>&1)"
echo "$listed" | grep -qx "$name" \
  || { { echo "AVD_HOME=$ANDROID_AVD_HOME USER_HOME=${ANDROID_USER_HOME:-}"; echo "list-avds: $listed"; find / -name "$name.ini" -not -path '/proc/*' 2>/dev/null; } | annotate "AVD not visible to emulator"; fail "emulator -list-avds does not show $name"; }

nohup "$emulator" -avd "$name" -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim \
  -no-snapshot -camera-back none -verbose > "$log_dir/emulator.log" 2>&1 &
emu_pid=$!

booted=0
deadline=$((SECONDS + ${BOOT_TIMEOUT_SECONDS:-600}))
while [ "$SECONDS" -lt "$deadline" ]; do
  sleep 5
  if ! kill -0 "$emu_pid" 2>/dev/null; then
    annotate "emulator exited early ($name)" < "$log_dir/emulator.log"
    fail "emulator process exited before boot"
  fi
  if [ "$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then booted=1; break; fi
done
if [ "$booted" != 1 ]; then
  annotate "emulator boot timeout ($name)" < "$log_dir/emulator.log"
  timeout 10 adb emu kill > /dev/null 2>&1 || true
  fail "boot did not complete within ${BOOT_TIMEOUT_SECONDS:-600}s"
fi
echo "::notice title=emulator preflight $name::booted after ${SECONDS}s"
timeout 10 adb emu kill > /dev/null 2>&1 || true
wait "$emu_pid" 2>/dev/null || true
exit 0
