plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

// AI coach domain — provider-agnostic model registry, fact grounding, prompt assembly, privacy
// redaction (PrivacyClass-aware), and a deterministic rule-based coach that needs no network.
// Pure JVM, exhaustively unit-tested; the Android layer wires providers + UI over this.
repositories { mavenCentral() }

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-wellness"))
    implementation(project(":core-body"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
