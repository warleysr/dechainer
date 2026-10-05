import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile = localProperties.getProperty("dechainer.storeFile")

android {
    namespace = "io.github.warleysr.dechainer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "io.github.warleysr.dechainer"
        minSdk = 30
        targetSdk = 36
        versionCode = 17
        versionName = "1.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    androidResources {
        noCompress.add("tflite")
        localeFilters += listOf("en", "pt")
    }

    packaging {
        jniLibs {
            // NsfwContentDetector only uses the classic Interpreter API on the CPU, which loads
            // just libLiteRt.so. The GPU accelerator (dlopen'd on demand, optional) and the JNI
            // bridge for the CompiledModel API are never used.
            excludes += listOf("**/libLiteRtClGlAccelerator.so", "**/liblitert_jni.so")
        }
    }

    dependenciesInfo {
        includeInApk = false
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("dechainer") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("dechainer.storePassword")
                keyAlias = localProperties.getProperty("dechainer.keyAlias")
                keyPassword = localProperties.getProperty("dechainer.keyPassword")
            }
        }
    }

    buildTypes {
        if (releaseStoreFile != null) {
            debug {
                signingConfig = signingConfigs.getByName("dechainer")
            }
        }
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("dechainer")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("user.language", "en")
                it.systemProperty("user.country", "US")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.biometric.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.timber)
    implementation(libs.jsoup)
    implementation(libs.json)
    implementation(libs.litert)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}