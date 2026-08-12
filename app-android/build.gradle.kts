import java.net.URI
plugins { id("com.android.application") version "8.7.3"; kotlin("android") version "2.1.0"; id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"; id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"; id("com.google.devtools.ksp") version "2.1.0-1.0.29" }
repositories { google(); mavenCentral() }
android {
 namespace="xyz.mdhv.formanalyser.app"; compileSdk=35
 defaultConfig { applicationId="xyz.mdhv.formanalyser"; minSdk=26; targetSdk=35; versionCode=6; versionName="0.6.0-spec-dev" }
 signingConfigs { getByName("debug") { storeFile=file("debug.keystore"); storePassword="android"; keyAlias="androiddebugkey"; keyPassword="android" } }
 buildTypes { release { isMinifyEnabled=false } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }
 kotlinOptions { jvmTarget="21" }
 buildFeatures { compose=true }
}
dependencies {
 implementation(project(":engine")); implementation(project(":archery-module")); implementation(project(":core-model")); implementation(project(":core-equipment")); implementation(project(":core-wellness")); implementation(project(":core-body")); implementation(project(":core-coach")); implementation(project(":core-exchange")); implementation(project(":core-scoring")); implementation(project(":core-athlete"))
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"); implementation("androidx.datastore:datastore-preferences:1.1.1"); implementation("com.google.crypto.tink:tink-android:1.15.0"); implementation("androidx.core:core-ktx:1.15.0"); implementation("androidx.activity:activity-compose:1.9.3"); implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7"); implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 val composeBom=platform("androidx.compose:compose-bom:2024.12.01"); implementation(composeBom); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-graphics"); implementation("androidx.compose.ui:ui-tooling-preview"); implementation("androidx.compose.material3:material3"); implementation("androidx.compose.material:material-icons-extended"); implementation("androidx.navigation:navigation-compose:2.8.5")
 implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1"); ksp("androidx.room:room-compiler:2.6.1")
 val camerax="1.4.1"; implementation("androidx.camera:camera-core:$camerax"); implementation("androidx.camera:camera-camera2:$camerax"); implementation("androidx.camera:camera-lifecycle:$camerax"); implementation("androidx.camera:camera-view:$camerax"); implementation("com.google.mediapipe:tasks-vision:0.10.20"); implementation("com.google.mediapipe:tasks-genai:0.10.24"); debugImplementation("androidx.compose.ui:ui-tooling")
}
val poseModelUrl="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
val downloadPoseModel by tasks.registering { description="Download the BlazePose model into src/main/assets"; val out=layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task"); outputs.file(out); doLast { val f=out.asFile; if(f.exists()&&f.length()>0)return@doLast; f.parentFile.mkdirs(); URI(poseModelUrl).toURL().openStream().use{input->f.outputStream().use{output->input.copyTo(output)}} } }
tasks.named("preBuild") { dependsOn(downloadPoseModel) }
