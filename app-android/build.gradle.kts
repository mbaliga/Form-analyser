// The Android capture app. NOT wired into the root settings.gradle.kts by default, because
// the Android Gradle Plugin needs the Android SDK (absent in headless CI). To build:
//   1. add `include(":app-android")` to ../settings.gradle.kts
//   2. ensure a local.properties with sdk.dir, or ANDROID_HOME, points at an Android SDK
//   3. ./gradlew :app-android:assembleDebug
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

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
val configuredVersionCode = providers.gradleProperty("crocodylVersionCode")
    .orElse("6")
    .map { raw ->
        val parsed = raw.toIntOrNull() ?: error("crocodylVersionCode must be an integer")
        require(parsed in 1..2_100_000_000) {
            "crocodylVersionCode must be between 1 and 2100000000"
        }
        parsed
    }
    .get()
val configuredVersionName = providers.gradleProperty("crocodylVersionName")
    .orElse("0.6.0-spec-dev")
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
    compileSdk = 35
    // Keep ordinary CI on the fast debug contract. The release workflow opts in after signing so
    // the same on-device smoke suite also exercises the minified production variant.
    testBuildType = if (instrumentRelease) "release" else "debug"
    defaultConfig {
        applicationId = "xyz.mdhv.formanalyser"
        minSdk = 26
        targetSdk = 35
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions { jvmTarget = "21" }
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
    // MediaPipe's AAR includes generated AutoValue classes, but its Maven metadata also pulls the
    // build-time processor into the runtime graph. The processor contributes no runtime behavior
    // and its service descriptor makes R8 inspect annotation processors inside the APK.
    implementation("com.google.mediapipe:tasks-vision:0.10.20") {
        exclude(group = "com.google.auto.value", module = "auto-value")
    }
    // MediaPipe LLM Inference (Gemma 3n) — on-device coach runtime.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
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
// globally. Only release outputs need the production-key check.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseSigning)
}

// Fetch the BlazePose model at build time and bundle it into assets, so the installed APK works
// with zero setup (no manual file drop, no first-run download). The endpoint is a convenience;
// the digest is the release contract, so a changed or corrupted "latest" response fails closed.
// Kept out of git; downloaded once and cached by the up-to-date check.
val poseModelUrl =
    "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
val poseModelSha256 = "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"
val downloadPoseModel by
    tasks.registering {
        description = "Download the BlazePose model into src/main/assets"
        val out = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task")
        outputs.file(out)
        doLast {
            val f = out.asFile
            fun sha256(file: java.io.File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }

            if (f.exists()) {
                check(f.isFile && f.length() > 0L && sha256(f) == poseModelSha256) {
                    "Bundled BlazePose model failed SHA-256 verification: ${f.absolutePath}"
                }
                return@doLast
            }

            f.parentFile.mkdirs()
            val staging = f.resolveSibling(".${f.name}.partial")
            try {
                URI(poseModelUrl).toURL().openStream().use { input ->
                    staging.outputStream().use { output -> input.copyTo(output) }
                }
                check(staging.isFile && staging.length() > 0L && sha256(staging) == poseModelSha256) {
                    "Downloaded BlazePose model failed SHA-256 verification"
                }
                check(staging.renameTo(f)) { "Could not publish the verified BlazePose model" }
            } catch (t: Throwable) {
                runCatching { staging.delete() }
                throw t
            }
        }
    }

tasks.named("preBuild") { dependsOn(downloadPoseModel) }
