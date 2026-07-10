// The Android capture app. NOT wired into the root settings.gradle.kts by default, because
// the Android Gradle Plugin needs the Android SDK (absent in headless CI). To build:
//   1. add `include(":app-android")` to ../settings.gradle.kts
//   2. ensure a local.properties with sdk.dir, or ANDROID_HOME, points at an Android SDK
//   3. ./gradlew :app-android:assembleDebug
import java.net.URI

plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
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
        versionCode = 3
        versionName = "0.5.0"
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

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
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

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Document vault — Tink streaming AEAD (androidx.security-crypto is deprecated; not used).
    implementation("com.google.crypto.tink:tink-android:1.15.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

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
}

// Fetch the BlazePose model at build time and bundle it into assets, so the installed APK works
// with zero setup (no manual file drop, no first-run download). Kept out of git; downloaded once
// and cached by the up-to-date check.
val poseModelUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"

val downloadPoseModel by tasks.registering {
    description = "Download the BlazePose model into src/main/assets"
    val out = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task")
    outputs.file(out)
    doLast {
        val file = out.asFile
        if (file.exists() && file.length() > 0) return@doLast
        file.parentFile.mkdirs()
        URI(poseModelUrl).toURL().openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        logger.lifecycle("Downloaded pose model (${file.length()} bytes)")
    }
}

tasks.named("preBuild") { dependsOn(downloadPoseModel) }
