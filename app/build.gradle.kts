import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Kotlin JVM toolchain + compiler options (Kotlin 2.4+ compilerOptions DSL).
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

android {
    compileSdk = 35
    namespace = "eu.kanade.tachiyomi.extension.ar.procomic"

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        applicationId = "eu.kanade.tachiyomi.extension.ar.procomic"

        resValue("string", "app_name", "ProComic")
        resValue("string", "source_lang", "ar")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Use debug keystore for sideloading; replace with production key for repo submission.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug { }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
            res.srcDirs("src/main/res")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}

dependencies {
    // Tachiyomi/Mihon extension library — provides HttpSource, SManga, SChapter, etc.
    // Group: keiyoushi (not tachiyomiorg). Version is a commit hash for the v16 API.
    // Confirmed: keiyoushi/extensions-source uses this exact coordinate.
    compileOnly("com.github.keiyoushi:extensions-lib:6e0c96cea8")

    // Kotlin + Serialization (bundled in APK; not provided by Tachiyomi at runtime)
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // OkHttp (provided by Tachiyomi app at runtime, not bundled)
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")

    // Jsoup (provided by Tachiyomi app at runtime, needed for interface compliance)
    compileOnly("org.jsoup:jsoup:1.16.2")
}
