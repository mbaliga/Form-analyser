// The Android capture app. NOT wired into the root settings.gradle.kts by default, because
// the Android Gradle Plugin needs the Android SDK (absent in headless CI). To build:
//   1. add `include(":app-android")` to ../settings.gradle.kts
//   2. ensure a local.properties with sdk.dir, or ANDROID_HOME, points at an Android SDK
//   3. ./gradlew :app-android:assembleDebug
import java.net.URI

plugins {
    // AGP pinned to match mbaliga/Hyle-Design-System exactly (8.9.1) — Gradle composite builds
    // (the `includeBuild("hyle-design-system")` in ../settings.gradle.kts that resolves
    // dev.aarso:hyle) require every participating build to use the SAME AGP version, or the
    // build fails with "Using multiple versions of the Android Gradle plugin ... is not allowed"
    // (Personal-Tracker DECISIONS.md D-Q, same constraint the earlier crash-recovery wiring hit).
    // The root Gradle wrapper is already 8.14.3, above AGP 8.9.1's minimum.
    id("com.android.application") version "8.9.1"
    kotlin("android") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    // Hilt DI (this pass — see CROCODYL_STATUS.md §4.4, previously deferred "to de-risk blind
    // compilation"). KSP-only: Hilt has supported a kapt-free KSP compiler since 2.48, so this adds
    // no second annotation-processing toolchain alongside Room's existing KSP setup above.
    id("com.google.dagger.hilt.android") version "2.52"
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "xyz.mdhv.formanalyser.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "xyz.mdhv.formanalyser"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0-spec-dev"
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
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    // buildConfig is on so ExportViewModel can stamp the REAL versionName into every .crocbak
    // manifest. It was a hand-copied constant and had drifted two releases stale (0.4.4 vs 0.6.0),
    // which is exactly the failure a generated constant cannot have.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Robolectric local unit tests (Room migrations, the PrivacyRegistry reflection guard, and
    // ViewModel tests) need real merged resources/assets on the test classpath — Robolectric's
    // AssetManager shadow reads the app's AndroidManifest.xml and resources this way rather than
    // through a device-shaped `androidTest` APK. See ":app-android"'s README / CROCODYL_STATUS.md
    // §4.4 for why these tests could only ever be authored blind here (no Android SDK in this
    // environment) and are a CI-only judge, same as every other Android-layer change in this repo.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // A shadow that has no real answer (e.g. an unshadowed platform call some transitive
            // Android-library code makes) returns a default (0/null/false) instead of throwing, so
            // one uncovered corner of the framework does not fail every test in the module.
            isReturnDefaultValues = true
        }
    }
    sourceSets {
        // Room's schema-export JSON (`room.schemaLocation` below) lands in `schemas/<db
        // class>/<version>.json`. Exposing that directory as a *test* asset source means that once
        // a real Android build (CI, since none is possible here) has populated it, a future
        // `MigrationTestHelper`-based fixture test can read prior-version schemas the same way an
        // instrumented test would — without a separate copy step. Today's migration tests
        // (AppDatabaseMigrationTest) do not depend on this: no schema JSON exists yet for versions
        // 1-8 (exportSchema was only just turned on at v9, and this environment cannot run the
        // Android/KSP build that would backfill them), so those tests instead replay each
        // migration's own DDL directly against a hand-built "before" fixture — see that file's KDoc.
        getByName("test") { assets.srcDir("$projectDir/schemas") }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // The free engine + archery module — local project dependencies, no external repo.
    implementation(project(":engine"))
    implementation(project(":archery-module"))
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
    // Real Hyle Design System tokens, resolved from the hyle-design-system includeBuild
    // (../settings.gradle.kts, gated behind -PwithAndroid) instead of the values hand-copied
    // into ui/theme/Theme.kt (see that file's KDoc). :hyle's own minSdk is 31 — above this
    // app's 26 — but its only Android surface is a namespace + two generated resource files
    // (hyle_tokens_colors.xml / hyle_tokens_dimens.xml); Hyle.kt/HyleTokens.kt are plain Kotlin
    // constants and sealed types with zero platform-API calls, so honoring app-android's real
    // minSdk via tools:overrideLibrary below (AndroidManifest.xml) does not risk a
    // NoSuchMethodError/ClassNotFoundException on API 26-30 devices.
    implementation("dev.aarso:hyle:0.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Document vault — Tink streaming AEAD (androidx.security-crypto is deprecated; not used).
    implementation("com.google.crypto.tink:tink-android:1.15.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Hilt: the DI container (Application graph + @HiltViewModel factories) and the Compose
    // integration that gives screens `hiltViewModel()` as a drop-in replacement for the old
    // `viewModel()` call. hilt-android-compiler runs through KSP, not kapt — see the plugin block.
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // CameraX — capture frames for pose estimation.
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // MediaPipe Pose (BlazePose) — on-device pose landmarks.
    implementation("com.google.mediapipe:tasks-vision:0.10.20")
    // MediaPipe LLM Inference (Gemma 3n) — on-device coach runtime.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Local unit tests (Robolectric) -----------------------------------------------------
    // Robolectric gives the JVM test source set the Android SDK's test-jar/manifest handling
    // (real `android.*` classes with working shadows — Room/SQLite, DataStore's file I/O, a
    // Context with a real filesDir) that plain JUnit against the android.jar stub cannot provide.
    // This is what lets Room migrations, the PrivacyRegistry reflection guard and the ViewModels
    // be exercised at all outside a device/emulator — see CROCODYL_STATUS.md §4.4 ("Robolectric
    // tests ... need the Android SDK") for why this was deferred until now, and this module's
    // header comment for why nothing here can be compiled or run in this environment either way.
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
    // MigrationTestHelper's API + the SupportSQLiteOpenHelper/FrameworkSQLiteOpenHelperFactory
    // seam AppDatabaseMigrationTest drives migrations through directly.
    testImplementation("androidx.room:room-testing:2.6.1")
    // viewModelScope's coroutines need a Main dispatcher; Dispatchers.setMain(...) in these tests
    // replaces it with one that runs eagerly on the calling thread instead of needing Robolectric's
    // (paused-by-default) main Looper pumped by hand.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Test doubles for the few collaborators that are concrete Android-Keystore-backed classes
    // (KeyVault via CoachLlmRouter, AndroidKeyProvider) rather than pure seams: mocking them is far
    // cheaper and more honest here than standing up a real AndroidKeyStore provider Robolectric
    // does not supply. Mockito 5's inline mock maker (bundled in mockito-core, no separate
    // mockito-inline artifact) mocks these final Kotlin classes without a javaagent.
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

// Fetch the BlazePose model at build time and bundle it into assets, so the installed APK works
// with zero setup (no manual file drop, no first-run download). Kept out of git; downloaded once
// and cached by the up-to-date check.
val poseModelUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
val downloadPoseModel by
    tasks.registering {
        description = "Download the BlazePose model into src/main/assets"
        val out = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task")
        outputs.file(out)
        doLast {
            val f = out.asFile
            if (f.exists() && f.length() > 0) return@doLast
            f.parentFile.mkdirs()
            URI(poseModelUrl).toURL().openStream().use { input ->
                f.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

tasks.named("preBuild") { dependsOn(downloadPoseModel) }
