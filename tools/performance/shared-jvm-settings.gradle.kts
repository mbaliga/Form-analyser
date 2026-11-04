pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "crocodyl-shared-jvm-performance"
include(":performance-analysis", ":local-session-core", ":search-core")
