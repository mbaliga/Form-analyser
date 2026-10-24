plugins {
    kotlin("jvm")
    application
}

group = "xyz.mdhv.crocodyl"
version = "0.6.0-beta"

kotlin { jvmToolchain(21) }

dependencies {
    implementation("dev.aarso:performance-analysis:0.1.0")
    implementation("dev.aarso:local-session-core:0.1.0")
    implementation("dev.aarso:search-core:0.2.0")
}

application {
    mainClass = "xyz.mdhv.crocodyl.performance.MainKt"
}
