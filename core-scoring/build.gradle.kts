plugins {
    kotlin("jvm")
}

group = "xyz.mdhv.formanalyser"
version = "0.6.0"

repositories { mavenCentral() }

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
