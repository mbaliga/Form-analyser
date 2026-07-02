plugins {
    kotlin("jvm") version "2.1.0"
    `maven-publish`
}

// The sport-agnostic Crocodyl engine: the personal-baseline -> deviation -> fatigue ->
// signal-score loop. It ships *inside* the free Form Analyser app. `maven-publish` is kept
// so the (paid) **Baseline EEG add-on** (the EEG Baseline product, `baseline` repo) can later consume it as a versioned artifact.
group = "xyz.mdhv.formanalyser"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "crocodyl-engine"
            from(components["java"])
        }
    }
}
