rootProject.name = "form-analyser"

// The archery sport-module: pure-Kotlin/JVM, headless-testable.
include(":archery-module")

// NOTE: the Android capture app (:app-android) is intentionally NOT wired into this build
// yet. It needs the Android Gradle Plugin + Android SDK, which aren't present in headless
// CI. Add `include(":app-android")` once building on a machine with the Android SDK.
// See app-android/README.md.
