plugins {
    kotlin("jvm")
}

// Load, ACWR, streak+hiatus, session duration, readiness, cycle estimation, privacy registry.
// Pure JVM, exhaustively unit-tested; every later phase leans on it.
repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
