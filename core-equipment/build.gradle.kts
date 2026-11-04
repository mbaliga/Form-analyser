plugins {
    kotlin("jvm")
}

// Equipment domain math (poundage estimation now; wear/service-life in Phase 4). Pure JVM.
repositories { mavenCentral() }

dependencies {
    implementation(project(":core-model"))
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
