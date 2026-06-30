rootProject.name = "form-analyser"

// The sport-agnostic Baseline engine, shipped inside the free app.
include(":engine")
// The archery sport-module: implements the engine's SportModule seam.
include(":archery-module")

// NOTE: the Android capture app (:app-android) is intentionally NOT wired into this build
// yet. It needs the Android Gradle Plugin + Android SDK, which aren't present in headless
// CI. Add `include(":app-android")` once building on a machine with the Android SDK.
// See app-android/README.md.
