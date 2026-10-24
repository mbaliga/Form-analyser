plugins {
    kotlin("jvm")
}

// Tiny shared vocabulary (Handedness, BowType) used across core modules + the app.
// Pure JVM, zero dependencies.
repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
