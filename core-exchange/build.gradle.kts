plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

// Phase 5 export/exchange — the consent filter that enforces PrivacyRegistry across export tiers,
// the .crocbak archive manifest, and pubkey identity. Pure JVM, exhaustively unit-tested; the
// Android layer only does SAF IO + the confirmation ceremony over this.
repositories { mavenCentral() }

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-wellness"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
