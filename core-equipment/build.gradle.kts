plugins {
    kotlin("jvm") version "2.1.0"
}

// Equipment domain math (poundage estimation now; wear/service-life in Phase 4). Pure JVM.
repositories { mavenCentral() }

dependencies {
    implementation(project(":core-model"))
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
