plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenLocal() // resolves the locally-published Baseline engine during dev/CI
    mavenCentral()
}

dependencies {
    // The sport-agnostic engine. The archery module implements its SportModule seam.
    // Published from the `baseline` repo via `./gradlew publishToMavenLocal`.
    implementation("xyz.mdhv.baseline:baseline-engine:0.1.0")

    testImplementation(kotlin("test"))
    testImplementation("xyz.mdhv.baseline:baseline-engine:0.1.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
