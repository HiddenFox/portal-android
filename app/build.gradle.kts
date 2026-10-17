plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "town.beings.portal"
    compileSdk = 36

    defaultConfig {
        applicationId = "town.beings.portal"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.1.9"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Portal is started as an executable from ApplicationInfo.nativeLibraryDir.
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    testImplementation(kotlin("test"))
}
