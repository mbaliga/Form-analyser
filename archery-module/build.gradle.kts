plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // The sport-agnostic engine — a local module in this repo, so Form Analyser builds
    // standalone with no external/private dependency.
    implementation(project(":engine"))

    testImplementation(kotlin("test"))
    testImplementation(project(":engine"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
