pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google() // for the Android Gradle Plugin (only consulted when :app-android is included)
    }
}

rootProject.name = "form-analyser"

// The sport-agnostic Crocodyl engine, shipped inside the free app.
include(":engine")
// The archery sport-module: implements the engine's SportModule seam.
include(":archery-module")

// :app-android needs the Android Gradle Plugin + Android SDK, absent in headless envs.
// Opt in with `-PwithAndroid` (CI's android job and any SDK-equipped machine); the default
// build stays SDK-free so `./gradlew test` runs the engine + archery anywhere.
if (startParameter.projectProperties.containsKey("withAndroid")) {
    include(":app-android")
}
