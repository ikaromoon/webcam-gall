plugins {
    id("com.android.application")
}

android {
    namespace = "it.gallipoli.webcam"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.gallipoli.webcam"
        minSdk = 22
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
