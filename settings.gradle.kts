pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
rootProject.name = "form-analyser"
include(":engine")
include(":archery-module")
include(":core-model")
include(":core-equipment")
include(":core-wellness")
include(":core-body")
include(":core-coach")
include(":core-exchange")
include(":core-scoring")
include(":core-athlete")
if (startParameter.projectProperties.containsKey("withAndroid")) {
    include(":app-android")

    // Narrow, deliberate: brings in mbaliga/Hyle-Design-System for its :hyle module
    // (dev.aarso:hyle — the design-token contract that Theme.kt used to hand-port). The
    // composite build also exposes :crash-recovery/:hyle-probe/:wallpaper, but app-android's
    // build.gradle.kts resolves only dev.aarso:hyle today. Gated behind the SAME -PwithAndroid
    // flag as :app-android, because :hyle is an Android-library project — including it
    // unconditionally would break the SDK-free `./gradlew test` (engine + archery + cores).
    // Update the pin with:
    //   git -C hyle-design-system fetch && git -C hyle-design-system checkout <sha> && git add hyle-design-system
    includeBuild("hyle-design-system")
}
