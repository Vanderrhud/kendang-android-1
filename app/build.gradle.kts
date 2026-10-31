plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vrkendang"
    compileSdk = 34

    // NDK r27+ diperlukan agar libc++_shared.so arm64 dan library kita kompatibel
    // dengan perangkat Android 15+ yang memakai page size 16 KB.
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.vrkendang"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        externalNativeBuild {
            cmake {
                // Membuat LOAD segment native 16 KB-aligned. Tanpa ini, dlopen gagal
                // di perangkat 16 KB dan aplikasi force close saat startup.
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // Memungkinkan unit test JVM memanggil android.util.Log tanpa crash.
        // Log call di SoundLoader cukup di-stub (tidak diverifikasi di test).
        unitTests.isReturnDefaultValues = true
        // Diperlukan Robolectric agar test View dapat memuat resource dan manifest Android.
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":kit-core"))
    // Dideklarasikan langsung karena :kit-core memakai serialization sebagai `implementation`,
    // sehingga API kotlinx.serialization tidak tersedia secara transitif di modul ini.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // 1.9.3 adalah rilis pertama Oboe dengan library prebuilt 16 KB-aligned.
    implementation("com.google.oboe:oboe:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation(kotlin("test"))
    testImplementation(project(":kit-core"))
    testImplementation("org.robolectric:robolectric:4.12.2")
}
