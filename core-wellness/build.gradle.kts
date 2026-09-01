plugins {
    kotlin("jvm") version "2.1.0"
}

// Load, ACWR, streak+hiatus, session duration, readiness, cycle estimation, privacy registry.
// Pure JVM, exhaustively unit-tested; every later phase leans on it.
repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
