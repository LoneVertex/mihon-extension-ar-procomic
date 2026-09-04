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
    // The AOMedia AVIF decoder supports the protected tile formats on the existing runtime floor.
    compileSdk = 35
    namespace = "eu.kanade.tachiyomi.extension.ar.procomic"

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        // Incremented so Mihon/Android cannot retain the previously installed failing APK.
        versionCode = 4
        versionName = "1.3"

        applicationId = "eu.kanade.tachiyomi.extension.ar.procomic"

        resValue("string", "app_name", "ProComic")
        resValue("string", "source_lang", "ar")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Leave release unsigned; maintainers must apply their controlled signing config at release time.
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

    // Extract bundled AVIF native libraries at install time. This avoids device-specific
    // direct-from-APK linker failures while preserving all four shipped ABIs.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Tachiyomi/Mihon extension library — provides HttpSource, SManga, SChapter, etc.
    // Group: keiyoushi (not tachiyomiorg). Version is a commit hash for the v16 API.
    // Confirmed: keiyoushi/extensions-source uses this exact coordinate.
    compileOnly("com.github.keiyoushi:extensions-lib:18a8e26be2")

    // Kotlin + Serialization (provided by Mihon at runtime — must NOT be bundled)
    // If kotlinx-serialization-json is bundled via 'implementation', our extension classes
    // end up in classes3.dex instead of classes.dex, making Mihon's ChildFirstPathClassLoader
    // unable to find them. Use compileOnly so Mihon's runtime provides these at load time.
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // OkHttp (provided by Tachiyomi app at runtime, not bundled)
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")

    // Jsoup (provided by Tachiyomi app at runtime, needed for interface compliance)
    // 1.23.1 includes the published security fix for malformed raw-text element handling.
    compileOnly("org.jsoup:jsoup:1.23.1")

    // Official AOMedia AVIF decoder for protected Reader tiles. It explicitly supports still AVIF
    // across 8/10/12-bit and YUV 420/422/444/monochrome variants and ships all four ABIs.
    implementation("org.aomedia.avif.android:avif:1.3.0.841110fd")
}
