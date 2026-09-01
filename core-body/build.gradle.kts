plugins {
    kotlin("jvm") version "2.1.0"
}

// Body-region ID contract + handedness-aware soreness-chip resolver (Phase 2).
// Atlas geometry (Phase 3) uses android.graphics and lands in the app module.
repositories { mavenCentral() }

dependencies {
    implementation(project(":core-model"))
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
