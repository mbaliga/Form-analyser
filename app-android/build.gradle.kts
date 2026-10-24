// The Android capture app. NOT wired into the root settings.gradle.kts by default, because
// the Android Gradle Plugin needs the Android SDK (absent in headless CI). To build:
//   1. add `include(":app-android")` to ../settings.gradle.kts
//   2. ensure a local.properties with sdk.dir, or ANDROID_HOME, points at an Android SDK
//   3. ./gradlew :app-android:assembleDebug
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseSigningProperties = Properties().apply {
    val file = rootProject.file("release-signing.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun releaseSigningValue(name: String): String? {
    val environmentName =
        when (name) {
            "storeFile" -> "STORE_FILE"
            "storePassword" -> "STORE_PASSWORD"
            "keyAlias" -> "KEY_ALIAS"
            "keyPassword" -> "KEY_PASSWORD"
            else -> name.uppercase()
        }
    return providers.environmentVariable("CROCODYL_RELEASE_$environmentName").orNull
        ?.takeIf { it.isNotBlank() }
        ?: releaseSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }
}

val releaseStoreFilePath = releaseSigningValue("storeFile")
val releaseStorePassword = releaseSigningValue("storePassword")
val releaseKeyAlias = releaseSigningValue("keyAlias")
val releaseKeyPassword = releaseSigningValue("keyPassword")
val releaseSigningReady = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }
// app-android/version.properties is the committed source of truth for the version, so a plain
// `./gradlew assembleRelease` (F-Droid builds the tag with no CI -P overrides) produces the same
// versionCode/versionName as the release workflow. -PcrocodylVersionCode/-PcrocodylVersionName
// remain explicit overrides for CI and one-off builds.
val versionProperties = Properties().apply {
    val file = layout.projectDirectory.file("version.properties").asFile
    check(file.isFile) { "Missing ${file.absolutePath}" }
    file.inputStream().use(::load)
}

fun versionProperty(name: String): String =
    versionProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("version.properties must define $name")

val configuredVersionCode = providers.gradleProperty("crocodylVersionCode")
    .orElse(versionProperty("versionCode"))
    .map { raw ->
        val parsed = raw.toIntOrNull() ?: error("crocodylVersionCode must be an integer")
        require(parsed in 1..2_100_000_000) {
            "crocodylVersionCode must be between 1 and 2100000000"
        }
        parsed
    }
    .get()
val configuredVersionName = providers.gradleProperty("crocodylVersionName")
    .orElse(versionProperty("versionName"))
    .get()
    .also { require(it.isNotBlank()) { "crocodylVersionName must not be blank" } }
val instrumentRelease = providers.gradleProperty("crocodylInstrumentRelease").isPresent

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "xyz.mdhv.formanalyser.app"
    compileSdk = 36
    // Keep ordinary CI on the fast debug contract. The release workflow opts in after signing so
    // the same on-device smoke suite also exercises the minified production variant.
    testBuildType = if (instrumentRelease) "release" else "debug"
    defaultConfig {
        applicationId = "com.asoc.crocodyl"
        minSdk = 26
        targetSdk = 36
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Reproducible, F-Droid-compatible output: no encrypted dependency-metadata blob (readable only
    // by Google) in the APK or bundle, and no VCS stamp (see the release build type below).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // A committed debug keystore so every CI build is signed with the SAME key. Without this,
    // assembleDebug uses the auto-generated ~/.android/debug.keystore, which GitHub Actions
    // regenerates each run — so each release APK had a different signature and Android refused to
    // install it over the previous one ("App not installed"). Debug-only key; safe to commit.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
            // META-INF/version-control-info.textproto embeds the commit; two checkouts of the same
            // source would then differ and the reproducible-build comparison would fail.
            vcsInfo {
                include = false
            }
        }
    }
    packaging {
        resources {
            // Annotation processors are build-time only; this descriptor is pulled into the app
            // transitively and makes R8 report a missing runtime service implementation.
            excludes += "META-INF/services/javax.annotation.processing.Processor"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    // buildConfig is on so ExportViewModel can stamp the REAL versionName into every .crocbak
    // manifest. It was a hand-copied constant and had drifted two releases stale (0.4.4 vs 0.6.0),
    // which is exactly the failure a generated constant cannot have.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    if (providers.gradleProperty("sharedLibrariesDir").isPresent) {
        sourceSets.getByName("main").java.srcDir("src/sharedSessionBeta/kotlin")
    }
}

// MediaPipe Tasks (tasks-core) ships a usage-statistics client (TasksStatsProtoLogger ->
// RemoteLoggingClient) that uploads package name, version, task name/mode and latency statistics
// to Google through the datatransport "cct" backend. Crocodyl has no analytics in any channel:
// the backend and runtime are excluded, and src/main/java/com/google/android/datatransport/
// provides a no-op TransportRuntime so the logger's events are dropped on device.
configurations.all {
    exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
    exclude(group = "com.google.android.datatransport", module = "transport-runtime")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    if (providers.gradleProperty("sharedLibrariesDir").isPresent) {
        implementation("dev.aarso:local-session-core:0.1.0")
    }
    // The free engine + archery module — local project dependencies, no external repo.
    implementation(project(":engine"))
    implementation(project(":archery-module"))
    implementation(project(":fencing-module"))
    implementation(project(":muaythai-module"))
    implementation(project(":bouldering-module"))
    // Pure-JVM cores (Phase 1+): shared model + equipment/poundage/wellness/body math.
    implementation(project(":core-model"))
    implementation(project(":core-equipment"))
    implementation(project(":core-wellness"))
    implementation(project(":core-body"))
    // Phase 3 AI coach (BYOK + on-device grounding/redaction) and Phase 5 export/exchange consent.
    implementation(project(":core-coach"))
    implementation(project(":core-exchange"))
    implementation(project(":core-scoring"))
    implementation(project(":core-athlete"))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Document vault — Tink streaming AEAD (androidx.security-crypto is deprecated; not used).
    implementation("com.google.crypto.tink:tink-android:1.15.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    // Keep runtime, KTX, compiler and migration test support on the same maintained Room release.
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    // CameraX — capture frames for pose estimation.
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    // MediaPipe Pose (BlazePose) — on-device pose landmarks.
    // MediaPipe's AAR includes generated AutoValue classes, but its Maven metadata also pulls the
    // build-time processor into the runtime graph. The processor contributes no runtime behavior
    // and its service descriptor makes R8 inspect annotation processors inside the APK.
    implementation("com.google.mediapipe:tasks-vision:0.10.26") {
        exclude(group = "com.google.auto.value", module = "auto-value")
    }
    // MediaPipe LLM Inference (Gemma 3n) — on-device coach runtime.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}

// Room's schema JSON is a migration fixture, not a disposable build artifact. Keeping it in the
// repository makes every supported version reviewable and lets CI catch a migration that no longer
// matches the entity model. Android builds generate/update the files under schemas/.
ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.absolutePath)
    arg("room.incremental", "true")
}

val verifyReleaseSigning by tasks.registering {
    doLast {
        check(releaseSigningReady) {
            "Release signing is not configured. Provide release-signing.properties or " +
                "CROCODYL_RELEASE_STORE_FILE, CROCODYL_RELEASE_STORE_PASSWORD, " +
                "CROCODYL_RELEASE_KEY_ALIAS and CROCODYL_RELEASE_KEY_PASSWORD."
        }
        val keystore = rootProject.file(requireNotNull(releaseStoreFilePath))
        check(keystore.isFile) {
            "Release signing keystore was not found at ${keystore.absolutePath}."
        }
    }
}

// AGP's shared pre-build task is also reached by debug unit-test variants, so do not gate it
// globally. Only release outputs need the production-key check, and only when the caller asks for
// it: the release workflow passes -PcrocodylRequireProductionSigning=true, while F-Droid and the
// reproducible-build job must be able to assemble an unsigned release.
if (providers.gradleProperty("crocodylRequireProductionSigning").isPresent) {
    tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        dependsOn(verifyReleaseSigning)
    }
}
