#!/usr/bin/env bash
# Settle a freshly booted CI emulator before instrumentation runs: the google_apis system images
# sometimes show "Pixel Launcher isn't responding" while first-boot work is still running, and that
# system dialog covers the app under test. Best-effort; never fails the lane.
set -u

dismiss() {
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
}

adb wait-for-device
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$scale" 0 >/dev/null 2>&1 || true
done
adb shell settings put secure anr_show_background 0 >/dev/null 2>&1 || true
adb shell settings put global show_first_crash_dialog_dev_option 0 >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
dismiss
sleep 30
dismiss
exit 0
