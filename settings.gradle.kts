pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// Foojay toolchain resolver: allows Gradle to auto-download JDK 21 for compilation
// even when the host JVM is Java 25 (which AGP 8.7 doesn't accept for the Gradle daemon).
// Must come AFTER pluginManagement block.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "procomic-extension"
include(":app")
