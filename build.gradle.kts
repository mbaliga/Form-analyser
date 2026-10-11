plugins {
    // Keep plugin versions in one place. Loading the Kotlin plugin with an explicit version in
    // several subprojects makes Gradle load it more than once and can lead to inconsistent
    // compiler behavior between modules.
    id("com.android.application") version "8.13.2" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    kotlin("android") version "2.3.21" apply false
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.compose") version "2.3.21" apply false
    kotlin("plugin.serialization") version "2.3.21" apply false
}

// Root build for Form Analyser (codename). Sport-specific archery code lives in
// :archery-module and depends on the sport-agnostic Baseline engine.
group = "xyz.mdhv.formanalyser"
version = "0.1.0"
