pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
rootProject.name = "form-analyser"
include(":engine")
include(":archery-module")
include(":core-model")
include(":core-equipment")
include(":core-wellness")
include(":core-body")
include(":core-coach")
include(":core-exchange")
include(":core-scoring")
include(":core-athlete")
if (startParameter.projectProperties.containsKey("withAndroid")) {
    include(":app-android")
}
