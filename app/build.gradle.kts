import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "2.3.20"
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

// ---- Release signing: reads keystore.properties (gitignored) ----
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "com.elbruz.vrvideo"

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.elbruz.vrvideo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 7
        versionName = "0.5.2"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // arm64-only release: modern devices incl. all MediaTek Helio G/Dimensity.
            // Keeps APK small (single ABI of ffmpeg+python native libs).
            // Debug builds also include x86_64 for emulator testing.
            abiFilters += if (gradle.startParameter.taskNames.any { it.contains("Release", true) }) {
                listOf("arm64-v8a")
            } else {
                listOf("arm64-v8a", "x86_64")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Image loading
    implementation(libs.coil)

    // Media / playback — handles standard + 360° spherical video
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.common)

    // yt-dlp + ffmpeg for fast segmented downloads
    implementation(libs.youtubedl)
    implementation(libs.youtubedl.ffmpeg)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.core)

    // Persistence
    implementation(libs.datastore)
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
