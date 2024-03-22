@file:Suppress("UnstableApiUsage")

rootProject.name = "metalava"

pluginManagement {
    repositories {
        // Prefer mavenCentral as that has signed artifacts
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":stub-annotations")