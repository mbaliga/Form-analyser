pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google() // for the Android Gradle Plugin (only consulted when :app-android is included)
    }
}

rootProject.name = "form-analyser"

// The sport-agnostic Baseline engine, shipped inside the free app.
include(":engine")
// The archery sport-module: implements the engine's SportModule seam.
include(":archery-module")

// Pure-JVM core modules (headless-testable): shared model, equipment math, wellness/load/ACWR/
// streak/readiness/cycle, and the body-region contract.
include(":core-model")
include(":core-equipment")
include(":core-wellness")
include(":core-body")

// :app-android needs the Android Gradle Plugin + Android SDK, absent in headless envs.
// Opt in with `-PwithAndroid` (CI's android job and any SDK-equipped machine); the default
// build stays SDK-free so `./gradlew test` runs the engine + archery anywhere.
if (startParameter.projectProperties.containsKey("withAndroid")) {
    include(":app-android")

    // Narrow, deliberate: brings in mbaliga/Hyle-Design-System ONLY for its :crash-recovery
    // module (dev.aarso:crash-recovery — no dependency on :hyle; this app has its own
    // FormAnalyserTheme). Gated behind the SAME -PwithAndroid flag as :app-android, because
    // its modules are Android-library projects — including it unconditionally would break the
    // SDK-free `./gradlew test` (engine + archery + cores). Update the pin with:
    //   git -C hyle-design-system fetch && git -C hyle-design-system checkout <sha> && git add hyle-design-system
    includeBuild("hyle-design-system")
}
