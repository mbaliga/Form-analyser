pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
rootProject.name = "form-analyser"

// Beta-only shared-library consumption. Production builds remain self-contained; beta verification
// supplies a pinned local checkout and consumes only the pure-JVM shared modules.
val sharedLibrariesDir = providers.gradleProperty("sharedLibrariesDir").orNull
if (sharedLibrariesDir != null) {
    includeBuild(file(sharedLibrariesDir)) {
        dependencySubstitution {
            substitute(module("dev.aarso:performance-analysis")).using(project(":performance-analysis"))
            substitute(module("dev.aarso:local-session-core")).using(project(":local-session-core"))
            substitute(module("dev.aarso:search-core")).using(project(":search-core"))
        }
    }
}

include(":engine")
include(":archery-module")
include(":fencing-module")
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
}
if (startParameter.projectProperties.containsKey("withSharedPerformance")) {
    include(":performance-report")
}
