plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.visionplay.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.visionplay.app"
        minSdk = 23
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "21").toIntOrNull() ?: 21
        versionName = "1.8.${System.getenv("GITHUB_RUN_NUMBER") ?: "21"}"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("VISIONPLAY_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("VISIONPLAY_STORE_PASSWORD")
                keyAlias = System.getenv("VISIONPLAY_KEY_ALIAS")
                keyPassword = System.getenv("VISIONPLAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.google.code.gson:gson:2.13.1")
}
